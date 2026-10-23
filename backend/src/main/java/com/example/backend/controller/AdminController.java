package com.example.backend.controller;

import com.example.backend.ai.action.AdminAction;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.DashboardChartsDTO;
import com.example.backend.dto.DashboardDTO;
import com.example.backend.dto.OrderDTO;
import com.example.backend.dto.ProductRequest;
import com.example.backend.dto.PromotionRequest;
import com.example.backend.dto.RefundDTO;
import com.example.backend.dto.ShipRequest;
import com.example.backend.dto.UserDTO;
import com.example.backend.entity.Category;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.entity.Product;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.RefundStatus;
import com.example.backend.entity.Review;
import com.example.backend.entity.ReviewReply;
import com.example.backend.entity.Shipment;
import com.example.backend.entity.ShipmentStatus;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.RequireAdmin;
import com.example.backend.security.RequirePermission;
import com.example.backend.service.AdminActionExecutor;
import com.example.backend.service.AdminService;
import com.example.backend.service.OrderService;
import com.example.backend.service.PaymentService;
import com.example.backend.service.ProductService;
import com.example.backend.service.PromotionService;
import com.example.backend.service.RefundService;
import com.example.backend.service.ReviewService;
import com.example.backend.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台管理接口（细粒度权限 RBAC），覆盖商品/订单/库存/会员/评论/分类/促销/支付/物流。
 * <p>类级不再统一 {@code @RequireAdmin}，改为各方法按业务需要标注 {@link RequirePermission} 或
 * {@link RequireAdmin}，由 {@link com.example.backend.security.AuthInterceptor} 实时校验用户当前
 * 角色的权限码集合。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final PromotionService promotionService;
    private final PaymentService paymentService;
    private final ShipmentService shipmentService;
    private final RefundService refundService;

    /**
     * 写操作执行器（幂等 + 审计）。
     * <p>本次改造只把它「包」在 5 个既有写接口外层：方法体原样搬进 lambda，
     * 路径/body/返回结构一律不变，因此老前端零改动。
     */
    private final AdminActionExecutor actionExecutor;

    public AdminController(AdminService adminService,
                           ProductService productService,
                           OrderService orderService,
                           ReviewService reviewService,
                           PromotionService promotionService,
                           PaymentService paymentService,
                           ShipmentService shipmentService,
                           RefundService refundService,
                           AdminActionExecutor actionExecutor) {
        this.adminService = adminService;
        this.productService = productService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.promotionService = promotionService;
        this.paymentService = paymentService;
        this.shipmentService = shipmentService;
        this.refundService = refundService;
        this.actionExecutor = actionExecutor;
    }

    // ==================== 仪表盘 ====================
    // 这两个方法刻意不加逐方法注解：仪表盘对「后台角色」（ADMIN/OPS/CS）统一开放（前端侧边栏
    // data-perm="" 也是全角色可见），由 AuthInterceptor 的 /api/admin/** 后台角色闸门兜底，
    // 普通用户（USER）拿不到任何后台接口 —— 早期漏标注解导致普通买家能读经营数据。

    @GetMapping("/dashboard")
    public ApiResponse<DashboardDTO> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/dashboard/charts")
    public ApiResponse<DashboardChartsDTO> dashboardCharts() {
        return ApiResponse.ok(adminService.charts());
    }

    // ==================== 会员管理 ====================
    // 列表/查看：member:view（CS+ADMIN）
    // 修改状态/角色：仅 ADMIN（避免客服能改用户角色）

    @GetMapping("/users")
    @RequirePermission({"member:view"})
    public ApiResponse<List<UserDTO>> users() {
        return ApiResponse.ok(adminService.listUsers().stream().map(UserDTO::from).toList());
    }

    @PutMapping("/users/{id}/status")
    @RequireAdmin
    public ApiResponse<UserDTO> updateUserStatus(@PathVariable Long id,
                                                 @RequestBody Map<String, Integer> body) {
        return ApiResponse.ok(UserDTO.from(adminService.updateUserStatus(id, body.get("status"))));
    }

    @PutMapping("/users/{id}/role")
    @RequireAdmin
    public ApiResponse<UserDTO> updateUserRole(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        return ApiResponse.ok(UserDTO.from(adminService.updateUserRole(id, body.get("role"))));
    }

    // ==================== 分类管理 ====================
    // 查看：product:view；增删改：product:edit（OPS+ADMIN）

    @GetMapping("/categories")
    @RequirePermission({"product:view"})
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(adminService.listAllCategories());
    }

    @PostMapping("/categories")
    @RequirePermission({"product:edit"})
    public ApiResponse<Category> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") == null ? null : Long.valueOf(body.get("parentId").toString());
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(body.get("sortOrder").toString());
        return ApiResponse.ok(adminService.createCategory(name, parentId, sortOrder));
    }

    @PutMapping("/categories/{id}")
    @RequirePermission({"product:edit"})
    public ApiResponse<Category> updateCategory(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer sortOrder = body.get("sortOrder") == null ? null : Integer.valueOf(body.get("sortOrder").toString());
        Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status").toString());
        return ApiResponse.ok(adminService.updateCategory(id, name, sortOrder, status));
    }

    @DeleteMapping("/categories/{id}")
    @RequirePermission({"product:edit"})
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ApiResponse.ok();
    }

    // ==================== 商品管理 ====================
    // 查看：product:view；增删改/库存：product:edit（OPS+ADMIN）

    @GetMapping("/products")
    @RequirePermission({"product:view"})
    public ApiResponse<List<Product>> products() {
        return ApiResponse.ok(adminService.listAllProducts());
    }

    @GetMapping("/products/low-stock")
    @RequirePermission({"product:view"})
    public ApiResponse<List<Product>> lowStockProducts() {
        return ApiResponse.ok(adminService.listLowStockProducts());
    }

    @PostMapping("/products")
    @RequirePermission({"product:edit"})
    public ApiResponse<Product> createProduct(@Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(productService.createProduct(req));
    }

    @PutMapping("/products/{id}")
    @RequirePermission({"product:edit"})
    public ApiResponse<Product> updateProduct(@PathVariable Long id,
                                              @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(productService.updateProduct(id, req));
    }

    @DeleteMapping("/products/{id}")
    @RequirePermission({"product:edit"})
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok();
    }

    /**
     * 调整库存：body {@code { "delta": 10 }}（正入库 / 负出库）。
     * <p>
     * <b>本次改造</b>：body 增加<b>可选</b> {@code expectedStock}（确认卡片上看到的库存快照）。
     * <ul>
     *   <li>不带 {@code expectedStock}（后台页面老代码、脚本）→ 走 {@code updateStock(id, delta)}，
     *       <b>行为与改前一模一样</b>（验收 ⑳）；</li>
     *   <li>带 {@code expectedStock}（对话卡片）→ 走新重载：行锁重读 + 快照比对，
     *       不一致抛 409 {@code CONFLICT_STOCK_CHANGED}，保证「补货不会补在过期快照上」（验收 ⑦）。</li>
     * </ul>
     */
    @PutMapping("/products/{id}/stock")
    @RequirePermission({"product:edit"})
    public ApiResponse<Product> updateStock(@PathVariable Long id,
                                            @RequestBody Map<String, Integer> body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
                                            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
                                            @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        Integer delta = body.getOrDefault("delta", 0);
        Integer expectedStock = body.get("expectedStock");
        Product product = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.STOCK_REPLENISH, id,
                        AdminActionExecutor.Command.params("delta", delta, "expectedStock", expectedStock),
                        proposalId, idempotencyKey, operatorId, operatorRole),
                Product.class,
                () -> productService.updateStock(id, delta, expectedStock));
        return ApiResponse.ok(product);
    }

    // ==================== 订单管理 ====================
    // 查看：order:view；发货：order:ship（OPS+ADMIN）

    @GetMapping("/orders")
    @RequirePermission({"order:view"})
    public ApiResponse<List<OrderDTO>> orders(@RequestParam(required = false) OrderStatus status) {
        return ApiResponse.ok(orderService.adminList(status));
    }

    /**
     * 订单发货。
     * <p>
     * <b>本次改造</b>：方法体原样搬进执行器 lambda。注意执行的仍是
     * {@code orderService.ship(id, req)} 这一整段既有逻辑（状态校验、运单号写入、物流表维护），
     * 一行没动；执行器只在外面加了「幂等 + 审计 + 与确认卡片参数一致性校验」。
     * 参数一致性校验能挡住「卡片上写的是 SF123，请求体却改成 SF999」（验收 ⑫）。
     */
    @PutMapping("/orders/{id}/ship")
    @RequirePermission({"order:ship"})
    public ApiResponse<OrderDTO> ship(@PathVariable Long id,
                                      @Valid @RequestBody ShipRequest req,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                      @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
                                      @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
                                      @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        Map<String, Object> params = AdminActionExecutor.Command.params(
                "company", req.getCompany(), "trackingNo", req.getTrackingNo());
        if (req.getRemark() != null && !req.getRemark().isBlank()) {
            params.put("remark", req.getRemark());
        }
        OrderDTO dto = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.ORDER_SHIP, id, params,
                        proposalId, idempotencyKey, operatorId, operatorRole),
                OrderDTO.class,
                () -> orderService.ship(id, req));
        return ApiResponse.ok(dto);
    }

    // ==================== 评论管理 ====================
    // 列表/删除/商家回复：review:reply（CS+ADMIN；AI 情感分析由 AiReviewController 处理）

    @GetMapping("/reviews")
    @RequirePermission({"review:reply"})
    public ApiResponse<List<Review>> reviews() {
        return ApiResponse.ok(reviewService.listAll());
    }

    @DeleteMapping("/reviews/{id}")
    @RequirePermission({"review:reply"})
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ApiResponse.ok();
    }

    /** 已由商家回复过的评论 ID 集合，列表页一次性拉取打「已回复」标。 */
    @GetMapping("/reviews/replied-ids")
    @RequirePermission({"review:reply"})
    public ApiResponse<Set<Long>> repliedReviewIds() {
        return ApiResponse.ok(reviewService.merchantRepliedReviewIds());
    }

    /** 某条评论下的全部回复（含用户追评与商家回复，按时间正序）。 */
    @GetMapping("/reviews/{id}/replies")
    @RequirePermission({"review:reply"})
    public ApiResponse<List<ReviewReply>> reviewReplies(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.listRepliesOrdered(id));
    }

    /**
     * 商家回复：后台客服/运营对用户评价的正式回复。
     * <p>
     * 与前台「追评」同表不同 {@code replyType}，因此用户能在商品详情页看到「商家回复」。
     */
    @PostMapping("/reviews/{id}/reply")
    @RequirePermission({"review:reply"})
    public ApiResponse<ReviewReply> replyReview(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @RequestAttribute(AuthInterceptor.ATTR_ROLE) String operatorRole,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String content = body == null ? null : body.get("content");
        String operatorName = switch (operatorRole == null ? "" : operatorRole) {
            case "ADMIN" -> "官方客服（管理员）";
            case "CS" -> "官方客服";
            case "OPS" -> "官方客服（运营）";
            default -> "官方客服";
        };
        return ApiResponse.ok(reviewService.addMerchantReply(operatorId, operatorName, id, content));
    }

    // ==================== 促销管理 ====================
    // 全部需 promotion:manage（OPS+ADMIN）

    @GetMapping("/promotions")
    @RequirePermission({"promotion:manage"})
    public ApiResponse<List<Promotion>> promotions() {
        return ApiResponse.ok(promotionService.listAll());
    }

    /**
     * 新建促销活动。
     * <p>
     * <b>本次改造</b>：{@code promotionService.create(req)} 原样搬进执行器 lambda。
     * 活动是纯 INSERT、没有状态机兜底，因此它的幂等键是「内容指纹」
     *（name|type|threshold|discount|productId|startTime|endTime 的 sha1）——
     * 连点两次卡片，第二次必然命中同一个键 → 只建一条（验收 ⑧）。
     * <p>
     * 注意 {@code targetId} 传 {@code null}：活动在确认前还不存在，没有主键可传。
     */
    @PostMapping("/promotions")
    @RequirePermission({"promotion:manage"})
    public ApiResponse<Promotion> createPromotion(@Valid @RequestBody PromotionRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
                                                 @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
                                                 @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        Map<String, Object> params = AdminActionExecutor.Command.params(
                "name", req.getName(),
                "type", req.getType() == null ? null : req.getType().name(),
                "threshold", req.getThreshold(),
                "discount", req.getDiscount(),
                "productId", req.getProductId());
        if (req.getStartTime() != null) params.put("startTime", req.getStartTime());
        if (req.getEndTime() != null) params.put("endTime", req.getEndTime());
        Promotion created = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.PROMOTION_CREATE, null, params,
                        proposalId, idempotencyKey, operatorId, operatorRole),
                Promotion.class,
                () -> promotionService.create(req));
        return ApiResponse.ok(created);
    }

    @PutMapping("/promotions/{id}")
    @RequirePermission({"promotion:manage"})
    public ApiResponse<Promotion> updatePromotion(@PathVariable Long id,
                                                  @Valid @RequestBody PromotionRequest req) {
        return ApiResponse.ok(promotionService.update(id, req));
    }

    @DeleteMapping("/promotions/{id}")
    @RequirePermission({"promotion:manage"})
    public ApiResponse<Void> deletePromotion(@PathVariable Long id) {
        promotionService.delete(id);
        return ApiResponse.ok();
    }

    // ==================== 退款管理 ====================
    // 列表/审核：order:view（OPS+ADMIN）或 cs:manage（客服角色描述里就含「退款审核」）
    // 同意退款会真正执行退回（回补库存/返还券/支付流水置已退款），因此只能在后台做。

    @GetMapping("/refunds")
    @RequirePermission({"order:view", "cs:manage"})
    public ApiResponse<List<RefundDTO>> refunds(@RequestParam(required = false) RefundStatus status) {
        return ApiResponse.ok(refundService.adminList(status));
    }

    /** 待处理退款申请数（侧边栏红点）。 */
    @GetMapping("/refunds/pending-count")
    @RequirePermission({"order:view", "cs:manage"})
    public ApiResponse<Map<String, Long>> refundPendingCount() {
        return ApiResponse.ok(Map.of("pending", refundService.pendingCount()));
    }

    /**
     * 同意退款。
     * <p>
     * <b>本次改造（T04）</b>：方法体原样搬进 {@link AdminActionExecutor#run} 的 lambda，
     * 由执行器负责「幂等 + 审计」。HTTP 契约不变：路径、权限注解、返回体都与改前一致；
     * 只是多了两个<b>可选</b>请求头 —— 对话入口的确认卡片会带
     * {@code Idempotency-Key}（防连点）与 {@code X-Proposal-Id}（找回提案行做审计与参数校验），
     * 后台页面老代码不带这两个头，走 {@code entry=PAGE} 分支，<b>只写审计、不做幂等</b>。
     */
    @PostMapping("/refunds/{id}/approve")
    @RequirePermission({"order:view", "cs:manage"})
    public ApiResponse<RefundDTO> approveRefund(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
            @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        RefundDTO dto = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.REFUND_APPROVE, id,
                        AdminActionExecutor.Command.params("decision", "APPROVE"),
                        proposalId, idempotencyKey, operatorId, operatorRole),
                RefundDTO.class,
                () -> refundService.approve(operatorId, id));
        return ApiResponse.ok(dto);
    }

    /**
     * 拒绝退款。
     * <p>与同意退款同构：{@code reason} 只有非空时才参与「与确认卡片一致」的校验 ——
     * 否则「运营没填理由、直接用服务端默认文案」会被误判成参数被篡改。
     */
    @PostMapping("/refunds/{id}/reject")
    @RequirePermission({"order:view", "cs:manage"})
    public ApiResponse<RefundDTO> rejectRefund(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
            @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        String reason = body == null ? null : body.get("reason");
        Map<String, Object> params = AdminActionExecutor.Command.params("decision", "REJECT");
        if (reason != null && !reason.isBlank()) {
            params.put("rejectReason", reason.trim());
        }
        RefundDTO dto = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.REFUND_REJECT, id, params,
                        proposalId, idempotencyKey, operatorId, operatorRole),
                RefundDTO.class,
                () -> refundService.reject(operatorId, id, reason));
        return ApiResponse.ok(dto);
    }

    // ==================== 支付管理 ====================
    // 全部需 order:view（OPS+ADMIN；退款/补录视为订单管理操作）

    @GetMapping("/payments")
    @RequirePermission({"order:view"})
    public ApiResponse<List<Payment>> payments() {
        return ApiResponse.ok(paymentService.listAll());
    }

    @PostMapping("/payments/refund/{id}")
    @RequirePermission({"order:view"})
    public ApiResponse<Payment> refund(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.refund(id));
    }

    @PostMapping("/payments/record")
    @RequirePermission({"order:view"})
    public ApiResponse<Payment> recordPayment(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        String orderNo = (String) body.get("orderNo");
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        PaymentMethod method = body.get("method") == null ? PaymentMethod.CASH
                : PaymentMethod.valueOf((String) body.get("method"));
        return ApiResponse.ok(paymentService.record(orderId, orderNo, userId, amount, method));
    }

    // ==================== 物流管理 ====================
    // 查看：order:view；状态变更：order:ship（OPS+ADMIN）

    @GetMapping("/shipments")
    @RequirePermission({"order:view"})
    public ApiResponse<List<Shipment>> shipments() {
        return ApiResponse.ok(shipmentService.listAll());
    }

    @PutMapping("/shipments/{id}/status")
    @RequirePermission({"order:ship"})
    public ApiResponse<Shipment> updateShipmentStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        ShipmentStatus status = ShipmentStatus.valueOf(body.get("status"));
        return ApiResponse.ok(shipmentService.updateStatus(id, status));
    }
}
