package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.RefundDTO;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentStatus;
import com.example.backend.entity.RefundRequest;
import com.example.backend.entity.RefundStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.RefundRequestRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款服务：用户提交申请 → 商家审核 → 同意才真正退回。
 * <p>
 * <b>核心口径：申请不等于退款。</b>
 * <ul>
 *   <li>用户点「申请退款」：只落一条 {@link RefundStatus#PENDING} 申请，订单置
 *       {@link OrderStatus#REFUNDING}；<b>钱不退、库存不回补、券不返还</b> —— 这些都要等商家同意。</li>
 *   <li>商家点「同意」：一次性执行退回 —— 回补库存与销量、返还优惠券、
 *       <b>余额支付的部分退回用户余额</b>、支付流水置 {@link PaymentStatus#REFUNDED}、
 *       订单置 {@link OrderStatus#REFUNDED}。</li>
 *   <li>商家点「拒绝」：订单恢复到申请前的状态（{@code previousStatus}），
 *       用户仍可继续使用/收货/评价。</li>
 * </ul>
 */
@Service
public class RefundService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 用户没填原因时的兜底文案，保证后台列表不留空。 */
    private static final String DEFAULT_REASON = "用户申请退款";

    private final RefundRequestRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final OrderService orderService;
    private final WalletService walletService;

    public RefundService(RefundRequestRepository refundRepository,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         PaymentRepository paymentRepository,
                         UserRepository userRepository,
                         DiscountService discountService,
                         OrderService orderService,
                         WalletService walletService) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.discountService = discountService;
        this.orderService = orderService;
        this.walletService = walletService;
    }

    // ==================== 用户侧 ====================

    /**
     * 用户提交退款申请。
     * <p>
     * 只允许「已付款之后」的订单申请（PAID / SHIPPED / COMPLETED）：
     * 待付款直接取消即可，已取消/已退款则无需再退。同一订单同时只允许一条待处理申请。
     */
    @Transactional
    public RefundDTO apply(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的订单");
        }
        switch (order.getStatus()) {
            case PENDING_PAYMENT -> throw new BusinessException(400, "订单尚未支付，无需退款，可直接取消订单");
            case CANCELLED -> throw new BusinessException(400, "订单已取消，无需退款");
            case REFUNDED -> throw new BusinessException(400, "该订单已退款");
            case REFUNDING -> throw new BusinessException(400, "该订单的退款申请正在处理中，请耐心等待");
            default -> { /* PAID / SHIPPED / COMPLETED 可申请 */ }
        }

        RefundRequest r = new RefundRequest();
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setUserId(userId);
        r.setAmount(order.getPayAmount());
        r.setReason(reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim());
        r.setStatus(RefundStatus.PENDING);
        // 记下申请前状态：商家拒绝时要原样恢复，否则订单会永远卡在「退款中」
        r.setPreviousStatus(order.getStatus());
        refundRepository.save(r);

        order.setStatus(OrderStatus.REFUNDING);
        orderRepository.save(order);

        return toDTO(r, order, userRepository.findById(userId).orElse(null));
    }

    /** 用户侧：某订单最新的退款申请（无则返回 null）。 */
    public RefundDTO latestOfOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看他人的订单");
        }
        return refundRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .map(r -> toDTO(r, order, null))
                .orElse(null);
    }

    // ==================== 商家侧（后台） ====================

    /** 后台退款申请列表（可按状态筛选，最新在前）。 */
    public List<RefundDTO> adminList(RefundStatus status) {
        List<RefundRequest> list = (status == null)
                ? refundRepository.findAllByOrderByIdDesc()
                : refundRepository.findByStatusOrderByIdDesc(status);
        if (list.isEmpty()) return new ArrayList<>();

        // 批量预取订单与用户，避免逐行回查（N+1）
        List<Long> orderIds = list.stream().map(RefundRequest::getOrderId).distinct().toList();
        Map<Long, Order> orderMap = new HashMap<>();
        for (Order o : orderRepository.findAllById(orderIds)) {
            orderMap.put(o.getId(), o);
        }
        Map<Long, List<OrderItem>> itemsMap = new HashMap<>();
        for (OrderItem it : orderItemRepository.findByOrderIdIn(orderIds)) {
            itemsMap.computeIfAbsent(it.getOrderId(), k -> new ArrayList<>()).add(it);
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userRepository.findAllById(
                list.stream().map(RefundRequest::getUserId).distinct().toList())) {
            userMap.put(u.getId(), u);
        }

        List<RefundDTO> out = new ArrayList<>();
        for (RefundRequest r : list) {
            RefundDTO dto = toDTO(r, orderMap.get(r.getOrderId()), userMap.get(r.getUserId()));
            List<OrderItem> items = itemsMap.get(r.getOrderId());
            if (items != null && !items.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (OrderItem it : items) {
                    if (sb.length() > 0) sb.append("，");
                    sb.append(it.getProductName()).append(" x").append(it.getQuantity());
                }
                dto.setProducts(sb.toString());
            }
            out.add(dto);
        }
        return out;
    }

    /** 待处理退款申请数（后台侧边栏红点）。 */
    public long pendingCount() {
        return refundRepository.countByStatus(RefundStatus.PENDING);
    }

    /**
     * 商家同意退款：真正执行退回。
     * <p>
     * 五件事必须一起做（同一事务）：回补库存与销量、返还优惠券、<b>余额支付的部分退回用户余额</b>、
     * 支付流水置已退款、订单置已退款。漏掉任何一件都会造成数据不一致 ——
     * 库存不回补则商品越退越少，券不返还则用户券货两空，余额不退回则用户余额支付的订单退款后钱凭空消失。
     */
    @Transactional
    public RefundDTO approve(Long operatorId, Long refundId) {
        RefundRequest r = getPending(refundId);
        Order order = orderRepository.findById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));

        // 禁止自审：审批人不能是退款申请人本人（否则可自行把余额退回自己账户）
        if (order.getUserId() != null && order.getUserId().equals(operatorId)) {
            throw new BusinessException(403, "不能审批自己提交的退款申请");
        }

        // 1) 回补库存与销量
        List<OrderItem> items = orderItemRepository.findByOrderId(r.getOrderId());
        orderService.restock(items);

        // 2) 返还优惠券（订单已失效，券该还给用户）
        discountService.releaseCoupon(order.getUserId(), order.getUserCouponId(), order.getId());

        // 3) 余额返现：如果这单是用余额支付的，钱要退回用户余额
        //    ⚠️ 必须在把支付流水置为 REFUNDED 之前调用，否则查不到 SUCCESS 流水，返现额算成 0
        walletService.refundOrderToBalance(order.getId(), order.getUserId());

        // 4) 支付流水置为已退款（只处理支付成功的那笔，失败流水不用动）
        for (Payment p : paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId())) {
            if (p.getStatus() == PaymentStatus.SUCCESS) {
                p.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(p);
            }
        }

        // 5) 订单置为已退款
        order.setStatus(OrderStatus.REFUNDED);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        r.setStatus(RefundStatus.APPROVED);
        r.setHandledBy(operatorId);
        r.setHandledAt(LocalDateTime.now());
        refundRepository.save(r);

        return toDTO(r, order, userRepository.findById(r.getUserId()).orElse(null));
    }

    /**
     * 商家拒绝退款：订单恢复到申请前的状态，商品该发还是发、该收还是收。
     */
    @Transactional
    public RefundDTO reject(Long operatorId, Long refundId, String rejectReason) {
        RefundRequest r = getPending(refundId);
        Order order = orderRepository.findById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));

        // previousStatus 理论上必有值；老数据兜底按「待发货」恢复，避免订单卡死在退款中
        OrderStatus back = r.getPreviousStatus() == null ? OrderStatus.PAID : r.getPreviousStatus();
        order.setStatus(back);
        orderRepository.save(order);

        r.setStatus(RefundStatus.REJECTED);
        r.setRejectReason(rejectReason == null || rejectReason.isBlank() ? "商家未通过退款审核" : rejectReason.trim());
        r.setHandledBy(operatorId);
        r.setHandledAt(LocalDateTime.now());
        refundRepository.save(r);

        return toDTO(r, order, userRepository.findById(r.getUserId()).orElse(null));
    }

    // ---------- 内部工具 ----------

    private RefundRequest getPending(Long refundId) {
        RefundRequest r = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(404, "退款申请不存在"));
        if (r.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(400, "该退款申请已" + r.getStatus().getLabel() + "，不能重复处理");
        }
        return r;
    }

    private RefundDTO toDTO(RefundRequest r, Order order, User user) {
        RefundDTO dto = new RefundDTO();
        dto.setId(r.getId());
        dto.setOrderId(r.getOrderId());
        dto.setOrderNo(r.getOrderNo());
        dto.setUserId(r.getUserId());
        dto.setAmount(r.getAmount());
        dto.setReason(r.getReason());
        dto.setStatus(r.getStatus().name());
        dto.setStatusLabel(r.getStatus().getLabel());
        dto.setRejectReason(r.getRejectReason());
        dto.setCreatedAt(fmt(r.getCreatedAt()));
        dto.setHandledAt(fmt(r.getHandledAt()));
        if (r.getPreviousStatus() != null) {
            dto.setPreviousStatus(r.getPreviousStatus().name());
            dto.setPreviousStatusLabel(r.getPreviousStatus().getLabel());
        }
        if (user != null) {
            dto.setUserName(user.getNickname() == null || user.getNickname().isBlank()
                    ? user.getUsername() : user.getNickname());
            dto.setUserPhone(user.getPhone());
        }
        if (order != null) {
            // 订单号以订单表为准（申请记录里的可能是历史快照）
            dto.setOrderNo(order.getOrderNo());
            if (dto.getAmount() == null) dto.setAmount(order.getPayAmount());
        }
        return dto;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(TIME_FMT);
    }
}
