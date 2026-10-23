package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.CheckoutPreviewDTO;
import com.example.backend.dto.CreateOrderRequest;
import com.example.backend.dto.OrderDTO;
import com.example.backend.dto.OrderItemDTO;
import com.example.backend.dto.ShipRequest;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.entity.PaymentStatus;
import com.example.backend.entity.Product;
import com.example.backend.entity.RefundRequest;
import com.example.backend.entity.Shipment;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.RefundRequestRepository;
import com.example.backend.repository.ReviewRepository;
import com.example.backend.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务：下单、查询、状态流转（支付/取消/发货/确认收货）。
 */
@Service
public class OrderService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final DiscountService discountService;
    private final RiskDetectionService riskDetectionService;
    private final ReviewRepository reviewRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final WalletService walletService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        PaymentRepository paymentRepository,
                        ShipmentRepository shipmentRepository,
                        DiscountService discountService,
                        RiskDetectionService riskDetectionService,
                        ReviewRepository reviewRepository,
                        RefundRequestRepository refundRequestRepository,
                        WalletService walletService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.discountService = discountService;
        this.riskDetectionService = riskDetectionService;
        this.reviewRepository = reviewRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.walletService = walletService;
    }

    /** 下单（整个方法在一个事务内：锁定库存 → 扣减 → 生成订单 → 清购物车）。 */
    @Transactional
    public OrderDTO create(Long userId, CreateOrderRequest req) {
        List<CartItem> items = resolveItems(userId, req);
        if (items.isEmpty()) {
            throw new BusinessException(400, "订单中没有任何商品");
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setReceiverAddress(req.getReceiverAddress());
        order.setRemark(req.getRemark());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem ci : items) {
            Product product = productRepository.findByIdForUpdate(ci.getProductId())
                    .orElseThrow(() -> new BusinessException(404, "商品不存在"));
            if (product.getStock() < ci.getQuantity()) {
                throw new BusinessException(400, "商品「" + product.getName() + "」库存不足");
            }
            // 扣库存 + 累加销量
            product.setStock(product.getStock() - ci.getQuantity());
            product.setSales(product.getSales() + ci.getQuantity());
            productRepository.save(product);

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            total = total.add(subtotal);

            OrderItem oi = new OrderItem();
            oi.setProductId(product.getId());
            oi.setProductName(product.getName());
            oi.setProductImage(product.getImageUrl());
            oi.setPrice(product.getPrice());
            oi.setQuantity(ci.getQuantity());
            oi.setSubtotal(subtotal);
            orderItems.add(oi);
        }

        order.setTotalAmount(total);

        // 优惠联动：自动满减/折扣 + 用户选用的券。
        // 走 DiscountService 而不是自己算，保证与「优惠试算」接口用的是同一段算法 ——
        // 页面上显示减 10、订单里就必须减 10，不允许两处各算一套。
        // 券不合规（不属于本人 / 已用 / 过期 / 未达门槛）会在这里直接抛异常，
        // 事务回滚，订单不会创建。
        CheckoutPreviewDTO preview = discountService.preview(userId, total, req.getUserCouponId());
        order.setDiscountAmount(preview.getDiscountAmount());
        order.setPromotionId(preview.getPromotionId());
        order.setUserCouponId(preview.getCouponId());
        order.setPayAmount(preview.getPayAmount());

        order = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrderId(order.getId());
        }
        orderItemRepository.saveAll(orderItems);

        // 券核销必须放在订单落库之后：要把自增的 orderId 写回券上便于溯源/退款回补
        discountService.consumeCoupon(userId, preview.getCouponId(), order.getId());

        // 若从购物车下单，下单成功后移除对应购物车项
        if (req.getCartItemIds() != null && !req.getCartItemIds().isEmpty()) {
            List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
            cartItemRepository.deleteByUserIdAndProductIdIn(userId, productIds);
        }

        // 异步触发风险扫描（不阻塞下单主流程，AI 失败也不影响订单创建）
        riskDetectionService.scanOrderAsync(order.getId());

        return toDTO(order, orderItems);
    }

    private List<CartItem> resolveItems(Long userId, CreateOrderRequest req) {
        // 优先：从购物车下单
        if (req.getCartItemIds() != null && !req.getCartItemIds().isEmpty()) {
            List<CartItem> selected = new ArrayList<>();
            for (Long cartId : req.getCartItemIds()) {
                CartItem ci = cartItemRepository.findById(cartId)
                        .orElseThrow(() -> new BusinessException(404, "购物车项不存在"));
                if (!ci.getUserId().equals(userId)) {
                    throw new BusinessException(403, "无权操作他人的购物车");
                }
                selected.add(ci);
            }
            return selected;
        }
        // 次之：立即购买
        if (req.getProductId() != null) {
            Product product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new BusinessException(404, "商品不存在"));
            int qty = req.getQuantity() == null ? 1 : req.getQuantity();
            if (qty < 1) throw new BusinessException(400, "数量至少为 1");
            CartItem ci = new CartItem();
            ci.setProductId(product.getId());
            ci.setQuantity(qty);
            return List.of(ci);
        }
        throw new BusinessException(400, "请指定购物车项或商品");
    }

    public List<OrderDTO> list(Long userId, OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        return toDTOList(orders, userId);
    }

    public OrderDTO detail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看他人的订单");
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Set<Long> reviewed = reviewedProductIds(userId, items);
        OrderDTO dto = toDTO(order, items, reviewed,
                shipmentRepository.findByOrderId(orderId).orElse(null),
                refundRequestRepository.findFirstByOrderIdOrderByIdDesc(orderId).orElse(null));
        dto.setReviewed(isOrderReviewed(items, reviewed));
        return dto;
    }

    /** 支付：待付款 → 待发货，并写入支付流水。method=BALANCE 时从余额扣款（不足则整单回滚）。 */
    @Transactional
    public OrderDTO pay(Long userId, Long orderId, PaymentMethod method) {
        Order order = getOwned(userId, orderId);
        requireStatus(order, OrderStatus.PENDING_PAYMENT, "仅待付款订单可支付");
        PaymentMethod use = method == null ? PaymentMethod.WECHAT : method;

        // 余额支付：先扣款（锁用户行 → 校验余额 → 写 PAY 流水）。余额不足会抛 400，
        // 使整个事务回滚 —— 订单留在「待付款」、余额不动、不产生支付流水。
        if (use == PaymentMethod.BALANCE) {
            walletService.payOrder(userId, order.getId(), order.getPayAmount());
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        // 支付联动：生成支付流水（记录用户实际选择的方式，不再硬编码 WECHAT）
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getPayAmount());
        payment.setMethod(use);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return toDTO(order, orderItemRepository.findByOrderId(orderId));
    }

    /** 取消：待付款 / 待发货 → 已取消，并回补库存；已付款订单取消视同退货退款（余额支付部分退回余额）。 */
    @Transactional
    public OrderDTO cancel(Long userId, Long orderId) {
        Order order = getOwned(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(400, "当前状态不可取消");
        }
        boolean wasPaid = order.getStatus() == OrderStatus.PAID;

        // 回补库存
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        restock(items);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        // 券回补：订单没了，券得还给用户（否则用户券和货两空）
        discountService.releaseCoupon(userId, order.getUserCouponId(), orderId);

        if (wasPaid) {
            // 已付款订单被取消 = 退货退款：
            // ⚠️ 必须先算返现额并退回余额，再把支付流水置为「已退款」——
            //    顺序颠倒会导致查不到 SUCCESS 流水、返现额算成 0，钱就丢了。
            walletService.refundOrderToBalance(order.getId(), userId);
            for (Payment p : paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())) {
                if (p.getStatus() == PaymentStatus.SUCCESS) {
                    p.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(p);
                }
            }
        }

        return toDTO(orderRepository.save(order), items);
    }

    /**
     * 回补订单内所有商品的库存与销量。
     * <p>
     * 「取消订单」与「商家同意退款」都要做同一件事，抽出来共用，避免两处逻辑漂移
     * （历史教训：售后流程漏回补库存，商品越卖越少）。
     */
    void restock(List<OrderItem> items) {
        if (items == null) return;
        for (OrderItem oi : items) {
            productRepository.findById(oi.getProductId()).ifPresent(p -> {
                p.setStock(p.getStock() + oi.getQuantity());
                if (p.getSales() >= oi.getQuantity()) {
                    p.setSales(p.getSales() - oi.getQuantity());
                }
                productRepository.save(p);
            });
        }
    }

    /** 确认收货：待收货 → 已完成。 */
    @Transactional
    public OrderDTO confirm(Long userId, Long orderId) {
        Order order = getOwned(userId, orderId);
        requireStatus(order, OrderStatus.SHIPPED, "仅待收货订单可确认收货");
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        return toDTO(orderRepository.save(order), orderItemRepository.findByOrderId(orderId));
    }

    /**
     * 发货（后台）：待发货 → 待收货，并写入物流记录。
     *
     * <h3>本次改造（QA Q-2）：补两个具体的 {@code subCode}</h3>
     * 改造前所有执行期失败都只有 {@code code=400 + message}，前端无法区分「状态不对」
     * 和「重复发货」，审计行也只能记下泛化的 {@code BUSINESS_ERROR}。
     * 现在分两种情况给出可判别的错误码，<b>message 文案逐字未变</b>（前端现有提示依赖它）。
     *
     * <h3>为什么「重复发货」要判在状态校验之前</h3>
     * 发过货的订单状态已经是 {@code SHIPPED}，若先判 {@code status != PAID} 就永远报
     * {@code ORDER_STATE_INVALID}，{@code DUPLICATE_SHIPMENT} 变成不可达分支（这正是
     * 改造前「重复发货拿不到 DUPLICATE_SHIPMENT」的原因）。已有 SHIPPED 物流记录是
     * 「发过货」的硬证据，用它先判更准确，话术也更贴近运营的认知（PRD A5-3）。
     */
    @Transactional
    public OrderDTO ship(Long orderId, ShipRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));

        // 1) 重复发货：已有 SHIPPED 物流记录 → 409 DUPLICATE_SHIPMENT
        Shipment existing = shipmentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null && existing.getStatus() == com.example.backend.entity.ShipmentStatus.SHIPPED) {
            throw new BusinessException(409, "DUPLICATE_SHIPMENT",
                    duplicateShipMessage(existing.getTrackingNo()));
        }

        // 2) 状态必须是待发货 → 400 ORDER_STATE_INVALID（message 保持原样）
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(400, "ORDER_STATE_INVALID", "仅待发货订单可发货");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 物流联动：生成/覆盖发货记录（复用上面已查到的那条，省一次查询）
        Shipment shipment = existing != null ? existing : new Shipment();
        shipment.setOrderId(order.getId());
        shipment.setOrderNo(order.getOrderNo());
        shipment.setCompany(req.getCompany());
        shipment.setTrackingNo(req.getTrackingNo());
        shipment.setStatus(com.example.backend.entity.ShipmentStatus.SHIPPED);
        shipment.setShippedAt(LocalDateTime.now());
        shipment.setRemark(req.getRemark());
        shipmentRepository.save(shipment);

        return toDTO(order, orderItemRepository.findByOrderId(orderId));
    }

    /** 重复发货的提示语：有运单号就把原来的运单号带出来，便于运营核对。 */
    private static String duplicateShipMessage(String trackingNo) {
        if (trackingNo == null || trackingNo.isBlank()) {
            return "这单已经发过了，不要重复发。";
        }
        return "这单已经发过了（运单号 " + trackingNo + "），不要重复发。";
    }

    /** 后台：全部订单（可按状态筛选）。 */
    public List<OrderDTO> adminList(OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findAll()
                : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        // 不计算 reviewed：该字段是「当前用户视角」的，后台跨用户统计没有意义，也省一次查询
        return toDTOList(orders, null);
    }

    // ---------- 内部工具 ----------

    private Order getOwned(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的订单");
        }
        return order;
    }

    private void requireStatus(Order order, OrderStatus expected, String errMsg) {
        if (order.getStatus() != expected) {
            throw new BusinessException(400, errMsg);
        }
    }

    private String generateOrderNo() {
        return "DR" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(100, 999);
    }


    /**
     * 批量组装订单 DTO（一次性预取明细、物流、退款，避免 N+1）。
     *
     * @param userId 传 null 表示「不计算 reviewed」（后台列表场景：该字段是当前用户视角的）
     */
    private List<OrderDTO> toDTOList(List<Order> orders, Long userId) {
        if (orders.isEmpty()) return new ArrayList<>();
        List<Long> ids = orders.stream().map(Order::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdIn(ids);

        // 一次性查出「该用户已评价过的商品」，避免每个订单各查一次（N+1）
        Set<Long> reviewedProductIds = null;
        if (userId != null) {
            Set<Long> productIds = allItems.stream()
                    .map(OrderItem::getProductId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            reviewedProductIds = productIds.isEmpty()
                    ? Set.of()
                    : new java.util.HashSet<>(reviewRepository.findReviewedProductIds(userId, productIds));
        }
        final Set<Long> reviewed = reviewedProductIds;

        Map<Long, Shipment> shipmentMap = loadShipments(ids);
        Map<Long, RefundRequest> refundMap = loadLatestRefunds(ids);

        return orders.stream()
                .map(o -> {
                    List<OrderItem> items = allItems.stream()
                            .filter(i -> i.getOrderId().equals(o.getId())).toList();
                    OrderDTO dto = toDTO(o, items, reviewed,
                            shipmentMap.get(o.getId()), refundMap.get(o.getId()));
                    if (reviewed != null) {
                        dto.setReviewed(isOrderReviewed(items, reviewed));
                    }
                    return dto;
                })
                .toList();
    }

    /** orderId → 物流记录（未发货的订单不在 map 里）。 */
    private Map<Long, Shipment> loadShipments(List<Long> orderIds) {
        Map<Long, Shipment> map = new java.util.HashMap<>();
        for (Shipment s : shipmentRepository.findByOrderIdIn(orderIds)) {
            map.putIfAbsent(s.getOrderId(), s);
        }
        return map;
    }

    /**
     * orderId → 最新一条退款申请。
     * <p>
     * 查询按 id 倒序，所以 putIfAbsent 留下的就是「每个订单最新那条」，不用再比时间。
     */
    private Map<Long, RefundRequest> loadLatestRefunds(List<Long> orderIds) {
        Map<Long, RefundRequest> map = new java.util.HashMap<>();
        for (RefundRequest r : refundRequestRepository.findByOrderIdInOrderByIdDesc(orderIds)) {
            map.putIfAbsent(r.getOrderId(), r);
        }
        return map;
    }

    /**
     * 订单是否已被该用户评价过：订单里所有商品都能在「已评价商品集合」里找到。
     * <p>
     * 单商品订单（绝大多数情况）等价于「该商品已评价」；多商品订单要求全部评完才算已评价，
     * 否则待评价列表里会漏掉还没评的那部分。
     */
    private static boolean isOrderReviewed(List<OrderItem> items, Set<Long> reviewedProductIds) {
        if (items == null || items.isEmpty()) return false;
        for (OrderItem it : items) {
            if (it.getProductId() == null || !reviewedProductIds.contains(it.getProductId())) {
                return false;
            }
        }
        return true;
    }

    /** 单订单的「已评价商品集合」（detail 用，单次查询）。 */
    private Set<Long> reviewedProductIds(Long userId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) return Set.of();
        Set<Long> productIds = items.stream()
                .map(OrderItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) return Set.of();
        return new java.util.HashSet<>(reviewRepository.findReviewedProductIds(userId, productIds));
    }

    private OrderDTO toDTO(Order o, List<OrderItem> items) {
        return toDTO(o, items, null,
                shipmentRepository.findByOrderId(o.getId()).orElse(null),
                refundRequestRepository.findFirstByOrderIdOrderByIdDesc(o.getId()).orElse(null));
    }

    /**
     * 组装订单 DTO。
     *
     * @param reviewedProductIds 该用户已评价过的商品 ID 集合；传 null 表示不计算（后台/下单等无用户视角的场景），
     *                           此时 {@code OrderItemDTO.reviewed} 保持 null。
     * @param shipment           物流记录，未发货传 null。
     * @param refund             最新一条退款申请，从未申请传 null。
     */
    private OrderDTO toDTO(Order o, List<OrderItem> items, Set<Long> reviewedProductIds,
                           Shipment shipment, RefundRequest refund) {
        OrderDTO dto = new OrderDTO();
        dto.setId(o.getId());
        dto.setOrderNo(o.getOrderNo());
        dto.setUserId(o.getUserId());
        dto.setTotalAmount(o.getTotalAmount());
        dto.setPayAmount(o.getPayAmount());
        dto.setDiscountAmount(o.getDiscountAmount());
        dto.setPromotionId(o.getPromotionId());
        dto.setUserCouponId(o.getUserCouponId());
        dto.setStatus(o.getStatus().name());
        dto.setStatusLabel(o.getStatus().getLabel());
        dto.setReceiverName(o.getReceiverName());
        dto.setReceiverPhone(o.getReceiverPhone());
        dto.setReceiverAddress(o.getReceiverAddress());
        dto.setRemark(o.getRemark());
        dto.setCreatedAt(fmt(o.getCreatedAt()));
        dto.setPaidAt(fmt(o.getPaidAt()));
        dto.setShippedAt(fmt(o.getShippedAt()));
        dto.setCompletedAt(fmt(o.getCompletedAt()));
        dto.setCancelledAt(fmt(o.getCancelledAt()));
        // 风险冗余字段：未扫描时三个字段都是 null，前端 riskBadgeOf 渲染"未扫描"
        dto.setRiskLevel(o.getRiskLevel() == null ? null : o.getRiskLevel().name());
        dto.setRiskScore(o.getRiskScore());
        dto.setRiskScannedAt(fmt(o.getRiskScannedAt()));

        // 物流：只有发过货的订单才有值，前台「已发货/待收货」列表靠它显示运单号
        if (shipment != null) {
            dto.setShipmentCompany(shipment.getCompany());
            dto.setTrackingNo(shipment.getTrackingNo());
        }
        // 退款：只有申请过才下发；前台据此显示「退款中 / 已退款」以及商家拒绝理由
        if (refund != null) {
            dto.setRefundStatus(refund.getStatus().name());
            dto.setRefundStatusLabel(refund.getStatus().getLabel());
            dto.setRefundReason(refund.getReason());
            dto.setRefundRejectReason(refund.getRejectReason());
            dto.setRefundRequestedAt(fmt(refund.getCreatedAt()));
        }

        List<OrderItemDTO> itemDTOs = new ArrayList<>();
        for (OrderItem oi : items) {
            OrderItemDTO idto = new OrderItemDTO();
            idto.setId(oi.getId());
            idto.setProductId(oi.getProductId());
            idto.setProductName(oi.getProductName());
            idto.setProductImage(oi.getProductImage());
            idto.setPrice(oi.getPrice());
            idto.setQuantity(oi.getQuantity());
            idto.setSubtotal(oi.getSubtotal());
            // 逐件标记是否已评价：前台按「一行一件商品」展示，过滤必须细到商品这一级
            if (reviewedProductIds != null && oi.getProductId() != null) {
                idto.setReviewed(reviewedProductIds.contains(oi.getProductId()));
            }
            itemDTOs.add(idto);
        }
        dto.setItems(itemDTOs);
        return dto;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
