package com.example.backend.ai.tools;

import com.example.backend.ai.action.AdminAction;
import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.ai.memory.JpaChatMemoryStore;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.PromotionRequest;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Product;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.PromotionType;
import com.example.backend.entity.RefundRequest;
import com.example.backend.entity.RefundStatus;
import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.Shipment;
import com.example.backend.entity.ShipmentStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.RefundRequestRepository;
import com.example.backend.repository.RiskScoreRepository;
import com.example.backend.repository.ShipmentRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AdminService;
import com.example.backend.service.PromotionService;
import com.example.backend.service.RiskDetectionService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 「小运」的 6 个 {@code prepare_*} 只读预检工具。
 *
 * <h2>一句话原则：这里一行写库代码都没有</h2>
 * 模型没有写权限，这一点不是靠提示词保证的，而是靠<b>结构</b>：
 * 本类的每个方法只「读库 + 算预检 + 返回提案素材」，返回值里
 * 只有 {@code {action, targetId, params, precheck, snapshot, fields}}，
 * <b>没有 path、没有 method</b>。真正的执行发生在前端点击确认卡片之后，
 * 由既有 REST 接口完成（和后台页面走同一条路径）。
 * <p>
 * 自检口径（回归红线）：在本文件里检索 JPA 写方法调用 —— save / delete / update 语句 ——
 * <b>出现次数必须为 0</b>，只允许 find* / exists* / count* 这类只读方法。
 * 因此本类<b>刻意不注入</b> {@code AdminActionLogRepository} / {@code RefundService} /
 * {@code OrderService} / {@code ProductService} —— 只要那几个对象不在手上，
 * 就算哪天有人手滑也不会在这里写出「对话能退、页面不能退」的第二条写路径。
 * 唯一注入的 {@link PromotionService} 只用到它的
 * {@link PromotionService#validateForPreview} 与 {@link PromotionService#findDuplicates}
 * （纯读 + 纯校验）。
 *
 * <h2>身份从哪来（模型伪造不了）</h2>
 * 操作人身份来自 {@link AdminActionContext}（ThreadLocal，由 Controller 绑定），
 * <b>不进模型 Schema</b>；同时用 {@code @ToolMemoryId} 的 memoryKey 反解 userId 做交叉校验
 * （{@code OPERATOR_MISMATCH}）。上下文缺失（例如工具被调度到别的线程）时一律返回
 * {@code CONTEXT_MISSING} 并停止，<b>绝不降级为跳过权限继续干</b>。
 *
 * <h2>为什么「预检一次算完」</h2>
 * 运营最烦的是「填完才告诉我没权限」。所以除了权限（必须先判，避免无权限者用预检
 * 探到对象是否存在）之外，状态类、业务规则类的检查一次算全，
 * 用 {@code precheck.checks[]} 一次交回；前端默认展示前 3 条，其余折叠。
 *
 * <h2>失败为什么不抛异常</h2>
 * 统一返回 {@code {"ok":false,"subCode":...,"message":...}}，让模型能用运营听得懂的话解释，
 * 而不是把整条对话链路打断成 500。{@code subCode} 与 PRD ⑧ 的 28 条错误码一一对应。
 */
@Component
public class AdminActionTools {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TS_FRIENDLY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 退款/补货/活动/发货的运单号格式（PRD A5-3）。 */
    private static final Pattern TRACKING_NO = Pattern.compile("^[A-Za-z0-9-]{6,32}$");

    /** 退款申请只有三种状态，这里用于生成人话。 */
    private static final String IDENTITY_LOST = "身份上下文丢失，请重试一次";
    private static final String IDENTITY_MISMATCH = "身份校验异常，请重试一次";
    private static final String DEFAULT_REJECT_REASON = "商家未通过退款审核";

    /** 单次补货上限（PRD A3-2）。 */
    private static final int MAX_DELTA = 100_000;
    /** 库存总量上限（PRD A3-3）。 */
    private static final int MAX_STOCK = 999_999;
    /** 关键词命中的候选展示上限（PRD A3-3：列前 5 + 更多请给商品 ID）。 */
    private static final int AMBIGUOUS_PREVIEW = 5;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RefundRequestRepository refundRepository;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final PromotionService promotionService;

    /**
     * 风险消息一键已读要读「未读指纹」与各等级未读计数。
     * <p>复用 {@code RiskDetectionService}（而不是直接注入 repository），
     * 保证「未读」的口径与后台风险监控页、侧边栏红点<b>同源</b>。
     */
    private final RiskDetectionService riskDetectionService;

    /**
     * 只用于把结果 Map 序列化成 JSON 串（与 {@code AdminDataTools} 返回字符串的风格一致）。
     * <p>
     * 不注入容器里的全局 ObjectMapper：这里不参与 HTTP 序列化，
     * 独立实例可以保证「将来有人给全局 ObjectMapper 加特性」不会意外改变工具返回格式。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminActionTools(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            RefundRequestRepository refundRepository,
                            ProductRepository productRepository,
                            ShipmentRepository shipmentRepository,
                            UserRepository userRepository,
                            PromotionService promotionService,
                            RiskDetectionService riskDetectionService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.refundRepository = refundRepository;
        this.productRepository = productRepository;
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.promotionService = promotionService;
        this.riskDetectionService = riskDetectionService;
    }

    // =====================================================================
    //  1. 退款审批（同意 / 拒绝已有申请）
    // =====================================================================

    @Tool(name = "prepare_refund_decision", value = """
            准备一笔退款审批（同意 / 拒绝已存在的退款申请）。仅当用户明确要执行退款审批时调用。
            本工具只做预检并生成"待确认提案"，不会执行任何写操作；真正的执行必须由用户在前端点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已退款"。
            若 precheck.ok=false，把不通过原因讲清楚，不要重试同一个操作。
            注意：后台只能审批"买家已提交"的申请，不能代客发起退款；用户说"给这单退了吧"但订单没有申请单时，请说明需买家先在 App 提交。""")
    public String prepareRefundDecision(
            @ToolMemoryId String memoryKey,
            @P(value = "退款单 ID；与 orderNo 二选一，优先用本参数", required = false) Long refundRequestId,
            @P(value = "订单号，形如 DR20261008120034123；refundRequestId 缺失时用它反查该订单最新一条申请",
                    required = false) String orderNo,
            @P("决策：APPROVE=同意退款，REJECT=拒绝退款") String decision,
            @P(value = "拒绝理由（仅 REJECT 时填，≤255 字；不填用默认『商家未通过退款审核』）",
                    required = false) String rejectReason) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            // ---- 1) 权限：必须最先判。先判权限再查对象，避免无权限者用预检探到「这条退款单存在」
            boolean perm = ctx.hasAnyPerm("order:view", "cs:manage");
            checks.add(check("PERMISSION", perm,
                    perm ? "具备 order:view 或 cs:manage" : "缺少「查看订单」或「客服管理」权限"));
            if (!perm) {
                return fail("PERM_DENIED",
                        "审批退款需要「查看订单」或「客服管理」权限，请让有此权限的同事处理。",
                        null, null, checks);
            }

            // ---- 2) 参数：决策与定位参数
            String normalizedDecision = normalizeDecision(decision);
            boolean hasTargetArg = (refundRequestId != null && refundRequestId > 0)
                    || (orderNo != null && !orderNo.isBlank());
            if (!hasTargetArg) {
                checks.add(check("PARAM", false, "缺少退款单 ID 或订单号"));
                return fail("PARAM_MISSING", "要处理哪一条退款？给我退款单号或订单号都可以。",
                        null, null, checks);
            }
            if (normalizedDecision == null) {
                checks.add(check("PARAM", false, "无法判断是同意还是拒绝"));
                return fail("PARAM_INVALID", "没看懂是同意还是拒绝，请说「通过」或「拒绝」。",
                        null, null, checks);
            }
            checks.add(check("PARAM", true, "参数完整（决策=" + normalizedDecision + "）"));

            // ---- 3) 定位对象
            RefundRequest refund;
            if (refundRequestId != null && refundRequestId > 0) {
                refund = refundRepository.findById(refundRequestId).orElse(null);
                if (refund == null) {
                    checks.add(check("TARGET", false, "退款申请不存在"));
                    return fail("TARGET_NOT_FOUND",
                            "没找到这条退款申请（ID " + refundRequestId + "），核对一下单号？",
                            null, null, checks);
                }
            } else {
                Order byOrderNo = orderRepository.findByOrderNo(orderNo.trim()).orElse(null);
                if (byOrderNo == null) {
                    checks.add(check("TARGET", false, "订单不存在"));
                    return fail("TARGET_NOT_FOUND",
                            "没找到订单 " + orderNo.trim() + "，核对一下订单号？", null, null, checks);
                }
                refund = refundRepository.findFirstByOrderIdOrderByIdDesc(byOrderNo.getId()).orElse(null);
                if (refund == null) {
                    checks.add(check("TARGET", false, "该订单没有退款申请"));
                    return fail("TARGET_NOT_FOUND",
                            "订单 " + byOrderNo.getOrderNo() + " 没有提交过退款申请（后台不能代客发起退款），"
                                    + "需要买家先在 App 里发起。", null, null, checks);
                }
            }
            checks.add(check("TARGET", true, "退款单 #" + refund.getId() + " 已定位"));

            Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
            if (order == null) {
                String message = "退款申请关联的订单不存在（数据异常），请联系技术处理。";
                checks.add(check("ORDER", false, message));
                return fail("TARGET_NOT_FOUND", message, null, refund.getId(), checks);
            }

            // ---- 4) 状态与业务规则：一次算全，再按优先级拦截
            boolean stillPending = refund.getStatus() == RefundStatus.PENDING;
            checks.add(check("REFUND_PENDING", stillPending,
                    stillPending ? "退款单待处理"
                            : "退款单已" + refund.getStatus().getLabel() + "，不能重复处理"));

            boolean orderRefunding = order.getStatus() == OrderStatus.REFUNDING;
            checks.add(check("ORDER_REFUNDING", orderRefunding,
                    orderRefunding ? "订单处于退款中"
                            : "订单当前是【" + order.getStatus().getLabel() + "】，不在退款流程里"));

            // 与 RefundService.approve/reject 里的「禁止自审」用同一个表达式（对照 RefundService:190）
            boolean selfApproval = order.getUserId() != null && order.getUserId().equals(ctx.operatorId());
            checks.add(check("SELF_APPROVAL", !selfApproval,
                    selfApproval ? "审批人就是买家本人，不能自审" : "审批人非买家"));

            if (!stillPending) {
                return fail("REFUND_ALREADY_HANDLED",
                        "这条退款已经" + refund.getStatus().getLabel() + "了，不能重复处理。",
                        null, refund.getId(), checks);
            }
            if (!orderRefunding) {
                return fail("ORDER_STATE_INVALID",
                        "订单当前状态是【" + order.getStatus().getLabel() + "】，不在退款流程中，刷新后重试。",
                        null, refund.getId(), checks);
            }
            if (selfApproval) {
                return fail("SELF_APPROVAL_FORBIDDEN",
                        "这单的买家就是你自己，按规则不能审批自己的退款。请让其他同事处理。",
                        null, refund.getId(), checks);
            }

            boolean approve = "APPROVE".equals(normalizedDecision);
            String reason = trimToNull(rejectReason);
            if (!approve) {
                if (reason != null && reason.length() > 255) {
                    checks.add(check("REASON_LENGTH", false, "拒绝理由超过 255 字"));
                    return fail("PARAM_INVALID", "拒绝理由太长了（上限 255 字），精简一下。",
                            null, refund.getId(), checks);
                }
                checks.add(check("REASON_LENGTH", true, "拒绝理由长度合规"));
                if (reason == null) reason = DEFAULT_REJECT_REASON;
            }

            // ---- 5) 组装提案素材
            AdminAction action = approve ? AdminAction.REFUND_APPROVE : AdminAction.REFUND_REJECT;
            String buyerName = nicknameOf(order.getUserId());
            BigDecimal refundAmount = refund.getAmount();

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("decision", normalizedDecision);
            params.put("rejectReason", approve ? null : reason);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("refundStatus", refund.getStatus().name());
            snapshot.put("orderStatus", order.getStatus().name());
            // payAmount 是二次确认阈值的输入，必须由工具算好（不能等前端算）
            snapshot.put("payAmount", money(refundAmount));
            snapshot.put("orderPayAmount", money(order.getPayAmount()));
            snapshot.put("userId", order.getUserId());
            snapshot.put("userName", buyerName);
            snapshot.put("orderNo", order.getOrderNo());
            snapshot.put("refundReason", refund.getReason());
            snapshot.put("itemCount", countItems(order.getId()));

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(fieldTone("退款金额", "¥" + money(refundAmount), "danger"));
            fields.add(field("状态变化", approve
                    ? "退款单 待处理 → 已同意；订单 退款中 → 已退款"
                    : "退款单 待处理 → 已拒绝；订单 退款中 → " + previousStatusLabel(refund)));
            fields.add(fieldMono("订单号", order.getOrderNo()));
            fields.add(field("买家", buyerName));
            if (refund.getReason() != null && !refund.getReason().isBlank()) {
                fields.add(field("退款原因", refund.getReason()));
            }
            if (!approve) {
                fields.add(field("拒绝理由", reason));
            }
            fields.add(field("次要影响", approve
                    ? "同意后：回补库存、返还优惠券、支付流水置 REFUNDED，不可撤销"
                    : "拒绝后：不回补库存、不返券，订单恢复原状态"));

            List<String> riskReasons = new ArrayList<>();
            if (refundAmount != null && refundAmount.compareTo(new BigDecimal("500")) >= 0) {
                riskReasons.add("金额较大（¥" + money(refundAmount) + "），确认前请核对买家与退款理由");
            }
            Map<String, Object> risk = risk(
                    riskReasons.isEmpty() ? "LOW" : "MEDIUM",
                    riskReasons,
                    false,
                    null,
                    action == AdminAction.REFUND_APPROVE,
                    action == AdminAction.REFUND_APPROVE ? "退款执行后不可撤销" : "拒绝后订单会恢复到申请前的状态");

            String targetLabel = "退款单 #" + refund.getId() + " · " + order.getOrderNo()
                    + " · ¥" + money(refundAmount);
            return toJson(proposal(action, refund.getId(), targetLabel, params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  2. 订单风险分析（单条）
    // =====================================================================

    @Tool(name = "prepare_risk_scan", value = """
            准备一次订单风险分析（单条）。仅当用户明确要执行风险扫描时调用。
            本工具只做预检并生成"待确认提案"，不执行扫描、不写评分；执行必须由用户点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已分析完成"。
            批量扫描本期未开放（本工具只支持 mode=SINGLE）；用户要批量时请说明"一次只能扫一单"。""")
    public String prepareRiskScan(
            @ToolMemoryId String memoryKey,
            @P("订单号，形如 DR20261008120034123") String orderNo,
            @P(value = "SINGLE=只扫这一单（本期仅支持）；BATCH_UNSCANNED 为 P1，传了会提示未开放",
                    required = false) String mode,
            @P(value = "（P1）批量条件下单状态，默认 PAID", required = false) String batchStatus,
            @P(value = "（P1）批量条件：最近 N 天，1-30，默认 7", required = false) Integer createdWithinDays,
            @P(value = "（P1）批量上限 1-10，默认 5", required = false) Integer limit,
            @P(value = "已有评分是否重扫，默认 false", required = false) Boolean rescan) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            // 权限：risk:scan 单码。risk:view ≠ risk:scan —— 能看分数不等于能触发扫描
            boolean perm = ctx.hasAnyPerm("risk:scan");
            checks.add(check("PERMISSION", perm, perm ? "具备 risk:scan" : "缺少「风险扫描」权限"));
            if (!perm) {
                return fail("PERM_DENIED",
                        "你没有风险扫描权限（需要「风险扫描」），可以让运营同事代扫。", null, null, checks);
            }

            String normalizedMode = (mode == null || mode.isBlank()) ? "SINGLE" : mode.trim().toUpperCase();
            if (!"SINGLE".equals(normalizedMode)) {
                checks.add(check("MODE", false, "批量扫描本期未开放"));
                return fail("NOT_AVAILABLE_YET",
                        "批量扫描本期未开放，我可以逐单扫，一次一单。", null, null, checks);
            }
            checks.add(check("MODE", true, "单条扫描"));

            if (orderNo == null || orderNo.isBlank()) {
                checks.add(check("PARAM", false, "缺少订单号"));
                return fail("PARAM_MISSING", "要扫哪一个订单？给我订单号。", null, null, checks);
            }
            checks.add(check("PARAM", true, "订单号已提供"));

            String no = orderNo.trim();
            Order order = orderRepository.findByOrderNo(no).orElse(null);
            if (order == null) {
                checks.add(check("TARGET", false, "订单不存在"));
                return fail("TARGET_NOT_FOUND", "没找到这个订单号，核对一下？", null, null, checks);
            }
            checks.add(check("TARGET", true, "订单 " + no + " 已定位"));

            boolean stateOk = order.getStatus() == OrderStatus.PAID
                    || order.getStatus() == OrderStatus.SHIPPED
                    || order.getStatus() == OrderStatus.COMPLETED;
            checks.add(check("ORDER_STATE", stateOk,
                    stateOk ? "订单状态允许分析（" + order.getStatus().getLabel() + "）"
                            : "订单是【" + order.getStatus().getLabel() + "】，没有分析必要"));
            if (!stateOk) {
                return fail("ORDER_STATE_INVALID",
                        "这单是【" + order.getStatus().getLabel() + "】，没有做风险分析的必要。",
                        null, order.getId(), checks);
            }

            boolean rescanned = Boolean.TRUE.equals(rescan);
            boolean alreadyScanned = order.getRiskScannedAt() != null;
            checks.add(check("ALREADY_SCANNED", true, alreadyScanned && !rescanned
                    ? "该单已有评分，本次为重新评估（不拦截）"
                    : "该单尚未评分"));

            List<String> riskReasons = new ArrayList<>();
            if (alreadyScanned && !rescanned) {
                riskReasons.add("该单已有评分（" + valueOr(order.getRiskScore(), 0) + " 分 / "
                        + (order.getRiskScannedAt() == null ? "-" : order.getRiskScannedAt().format(TS_FRIENDLY))
                        + "），本次为重新评估");
            }
            String currentLevel = order.getRiskLevel() == null ? null : order.getRiskLevel().name();
            String riskLevel = "LOW";
            if ("HIGH".equals(currentLevel)) {
                riskLevel = "MEDIUM";
                riskReasons.add("该单当前为高风险，重新评估结果可能变化");
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("orderNo", no);
            params.put("mode", normalizedMode);
            params.put("rescan", rescanned);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("orderNo", order.getOrderNo());
            snapshot.put("amount", money(order.getPayAmount()));
            snapshot.put("status", order.getStatus().name());
            snapshot.put("paidAt", ts(order.getPaidAt()));
            snapshot.put("currentScore", order.getRiskScore());
            snapshot.put("currentLevel", currentLevel);
            snapshot.put("lastScannedAt", ts(order.getRiskScannedAt()));
            snapshot.put("waitedHours", waitedHours(order.getPaidAt()));

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(field("当前状态", order.getStatus().getLabel()));
            fields.add(field("订单金额", "¥" + money(order.getPayAmount())));
            fields.add(field("已等待", humanDuration(waitedHours(order.getPaidAt()))));
            fields.add(field("上次评分", alreadyScanned
                    ? valueOr(order.getRiskScore(), 0) + " 分 / " + order.getRiskScannedAt().format(TS_FRIENDLY)
                    : "尚未评分"));
            fields.add(field("说明", "本操作只打分，不改变订单状态；高危单仍可发货（仅提示）"));

            Map<String, Object> risk = risk(riskLevel, riskReasons, false, null, false,
                    "分析会调用一次 AI 综合评分（2-8 秒）");

            String targetLabel = "风险分析 · " + order.getOrderNo();
            return toJson(proposal(AdminAction.RISK_SCAN, order.getId(), targetLabel,
                    params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  2b. 风险消息一键已读（批量，收件箱级）
    // =====================================================================

    @Tool(name = "prepare_mark_risk_read", value = """
            准备一次「风险消息一键已读」：把风险监控里未读的消息批量标记为已读。
            仅当用户明确要求执行时调用（"把风险消息都标已读""清掉风险红点""风险提醒我一键读了"）。
            本工具只做预检并生成"待确认提案"，不改任何未读状态；执行必须由用户点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已经标为已读"。
            范围默认 ALL=全部未读；只有用户明确说"高风险的/只清中风险"时才传 HIGH/MEDIUM/LOW。
            【口径】"已读"只是运营侧的收件箱状态：不改风险分数、等级、建议处置，也不改订单状态。
            若返回 NO_UNREAD（当前没有未读），直接告诉用户不用处理，不要重试、不要编造条数。""")
    public String prepareMarkRiskRead(
            @ToolMemoryId String memoryKey,
            @P(value = "范围：ALL=全部未读（默认）；HIGH/MEDIUM/LOW=只标该等级", required = false) String level) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            // 权限用 risk:view —— 与页面上单条「标已读」接口同一个码。
            // 已读不改风控结论（分数/等级/建议处置都不动），因此不该要求 risk:scan。
            boolean perm = ctx.hasAnyPerm("risk:view");
            checks.add(check("PERMISSION", perm, perm ? "具备 risk:view" : "缺少「风险监控」查看权限"));
            if (!perm) {
                return fail("PERM_DENIED",
                        "你没有风险监控的查看权限（需要「风险监控」），可以让运营同事代处理。",
                        null, null, checks);
            }

            String scope = (level == null || level.isBlank()) ? "ALL" : level.trim().toUpperCase();
            RiskLevel scopeLevel;
            switch (scope) {
                case "ALL" -> scopeLevel = null;
                case "HIGH" -> scopeLevel = RiskLevel.HIGH;
                case "MEDIUM" -> scopeLevel = RiskLevel.MEDIUM;
                case "LOW" -> scopeLevel = RiskLevel.LOW;
                default -> {
                    checks.add(check("PARAM", false, "无法识别的范围: " + level));
                    return fail("PARAM_INVALID",
                            "范围只能是 ALL（全部未读）或 HIGH / MEDIUM / LOW，你要清哪一档？",
                            null, null, checks);
                }
            }
            checks.add(check("PARAM", true,
                    "范围：" + ("ALL".equals(scope) ? "全部未读" : scope + " 等级未读")));

            RiskScoreRepository.UnreadStat stat = riskDetectionService.unreadStat(scopeLevel);
            long unread = countOf(stat);
            if (unread <= 0) {
                checks.add(check("UNREAD", false, "当前没有未读的风险消息"));
                return fail("NO_UNREAD",
                        "ALL".equals(scope)
                                ? "现在没有未读的风险消息，不用处理。"
                                : "当前没有未读的 " + scope + " 级风险消息，不用处理。",
                        null, null, checks);
            }
            checks.add(check("UNREAD", true, "未读 " + unread + " 条"));

            // 各等级分布只为「点之前先看一眼」，不参与任何判定
            long highUnread = "ALL".equals(scope)
                    ? countOf(riskDetectionService.unreadStat(RiskLevel.HIGH))
                    : ("HIGH".equals(scope) ? unread : 0L);
            long mediumUnread = "ALL".equals(scope)
                    ? countOf(riskDetectionService.unreadStat(RiskLevel.MEDIUM))
                    : ("MEDIUM".equals(scope) ? unread : 0L);
            long lowUnread = "ALL".equals(scope)
                    ? countOf(riskDetectionService.unreadStat(RiskLevel.LOW))
                    : ("LOW".equals(scope) ? unread : 0L);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("level", scope);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("unreadCount", unread);
            snapshot.put("unreadMaxId", stat.getMaxId());
            snapshot.put("unreadScoreSum", stat.getScoreSum());
            snapshot.put("highUnread", highUnread);
            snapshot.put("mediumUnread", mediumUnread);
            snapshot.put("lowUnread", lowUnread);

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(field("待读消息", unread + " 条"));
            if ("ALL".equals(scope)) {
                fields.add(field("其中", "高风险 " + highUnread + " · 中风险 " + mediumUnread
                        + " · 低风险 " + lowUnread));
            }
            fields.add(field("说明", "只改「未读」标记：分数、等级、建议处置、订单状态都不变"));
            fields.add(field("已读之后", "侧边栏风险红点消失，记录仍留在风险监控里，可随时回看"));

            List<String> riskReasons = new ArrayList<>();
            if ("ALL".equals(scope) && highUnread > 0) {
                riskReasons.add("其中有 " + highUnread + " 条高风险未读；全部已读后红点会消失，"
                        + "建议先看过高风险单再一键清掉");
            }
            Map<String, Object> risk = risk(
                    riskReasons.isEmpty() ? "LOW" : "MEDIUM",
                    riskReasons, false, null, false,
                    "只影响未读状态，不改变任何风控结论");

            String targetLabel = "风险监控 · 未读 " + unread + " 条"
                    + (scopeLevel == null ? "" : "（仅 " + scope + "）");
            return toJson(proposal(AdminAction.RISK_READ_ALL, null, targetLabel,
                    params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  3. 补货
    // =====================================================================

    @Tool(name = "prepare_restock", value = """
            准备一次商品补货（增加库存）。仅当用户明确要执行补货时调用。
            本工具只做预检并生成"待确认提案"，不执行入库；执行必须由用户点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已补货"。
            预检会记录"确认时刻的库存快照"（expectedStock），执行时若库存已被改动会被拒绝（防止重复入库）。
            商品关键词命中多个时必须把候选列给用户选，禁止自行挑一个。""")
    public String prepareRestock(
            @ToolMemoryId String memoryKey,
            @P(value = "商品 ID；与 keyword 二选一，优先", required = false) Long productId,
            @P(value = "商品名关键词；productId 缺失时用它搜索。命中多个必须让用户选",
                    required = false) String keyword,
            @P(value = "DELTA=再加 N 件（默认）；TARGET=补到一共 N 件", required = false) String mode,
            @P(value = "DELTA 模式的增加量，1-100000", required = false) Integer delta,
            @P(value = "TARGET 模式的目标库存，0-100000", required = false) Integer targetStock) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            boolean perm = ctx.hasAnyPerm("product:edit");
            checks.add(check("PERMISSION", perm, perm ? "具备 product:edit" : "缺少「编辑商品」权限"));
            if (!perm) {
                return fail("PERM_DENIED", "补货需要「编辑商品」权限，找运营同事处理。", null, null, checks);
            }

            String normalizedMode = (mode == null || mode.isBlank()) ? "DELTA" : mode.trim().toUpperCase();
            if (!"DELTA".equals(normalizedMode) && !"TARGET".equals(normalizedMode)) {
                checks.add(check("PARAM", false, "补货模式无法识别"));
                return fail("PARAM_INVALID", "没看懂是「再加 N 件」还是「补到 N 件」，请说明一下。",
                        null, null, checks);
            }

            // ---- 定位商品
            Product product;
            if (productId != null && productId > 0) {
                product = productRepository.findById(productId).orElse(null);
                if (product == null) {
                    checks.add(check("TARGET", false, "商品不存在"));
                    return fail("TARGET_NOT_FOUND", "没找到这个商品，可能已删除。", null, null, checks);
                }
            } else {
                String kw = trimToNull(keyword);
                if (kw == null) {
                    checks.add(check("PARAM", false, "既没有商品 ID 也没有商品名"));
                    return fail("PARAM_MISSING", "是哪个商品？给我商品名我来搜，或者直接给商品 ID。",
                            null, null, checks);
                }
                List<Product> hits = new ArrayList<>();
                for (Product p : productRepository.findAll()) {
                    if (p.getName() != null && p.getName().contains(kw)) {
                        hits.add(p);
                    }
                }
                if (hits.isEmpty()) {
                    checks.add(check("TARGET", false, "关键词没有命中任何商品"));
                    return fail("TARGET_NOT_FOUND", "没搜到「" + kw + "」这个商品，换个关键词试试？",
                            null, null, checks);
                }
                if (hits.size() > 1) {
                    // 命中多个必须让用户选：模型自行挑一个是「补错货」的直接原因
                    StringBuilder candidates = new StringBuilder();
                    for (int i = 0; i < Math.min(hits.size(), AMBIGUOUS_PREVIEW); i++) {
                        Product p = hits.get(i);
                        if (i > 0) candidates.append("；");
                        candidates.append(p.getName()).append("（ID ").append(p.getId())
                                .append("，库存 ").append(valueOr(p.getStock(), 0)).append("）");
                    }
                    String message = "「" + kw + "」匹配到 " + hits.size() + " 个商品，你要补哪个？"
                            + candidates + (hits.size() > AMBIGUOUS_PREVIEW ? "；更多请直接给商品 ID" : "");
                    checks.add(check("TARGET", false, "关键词命中多个商品"));
                    return fail("AMBIGUOUS_TARGET", message, null, null, checks);
                }
                product = hits.get(0);
            }
            checks.add(check("TARGET", true, "商品「" + product.getName() + "」已定位"));

            int currentStock = valueOr(product.getStock(), 0);

            // ---- 数量换算：TARGET 模式的 delta 必须在服务端算，不能在前端算
            int finalDelta;
            String conversionNote = null;
            if ("TARGET".equals(normalizedMode)) {
                if (targetStock == null) {
                    checks.add(check("QTY", false, "缺少目标库存"));
                    return fail("PARAM_MISSING", "补到多少件？给我目标库存。", null, product.getId(), checks);
                }
                if (targetStock < 0 || targetStock > MAX_DELTA) {
                    checks.add(check("QTY", false, "目标库存超出范围"));
                    return fail("PARAM_INVALID", "目标库存要在 0 到 10 万件之间。",
                            null, product.getId(), checks);
                }
                if (targetStock < currentStock) {
                    checks.add(check("QTY", false, "补货只能增加库存"));
                    return fail("PARAM_INVALID",
                            "补货只能增加库存（当前 " + currentStock + " 件），减库存请到商品管理页。",
                            null, product.getId(), checks);
                }
                finalDelta = targetStock - currentStock;
                conversionNote = "按确认时刻库存换算：" + targetStock + " - " + currentStock
                        + " = +" + finalDelta;
                checks.add(check("QTY", true, conversionNote));
            } else {
                if (delta == null) {
                    checks.add(check("QTY", false, "缺少补货数量"));
                    return fail("PARAM_MISSING", "加多少件？", null, product.getId(), checks);
                }
                if (delta < 1 || delta > MAX_DELTA) {
                    checks.add(check("QTY", false, "补货数量超出范围"));
                    return fail("PARAM_INVALID", "补货数量要大于 0，最多 10 万件。",
                            null, product.getId(), checks);
                }
                finalDelta = delta;
                checks.add(check("QTY", true, "补货 " + finalDelta + " 件"));
            }

            int stockAfter = currentStock + finalDelta;
            boolean rangeOk = stockAfter >= 0 && stockAfter <= MAX_STOCK;
            checks.add(check("RANGE", rangeOk,
                    rangeOk ? "补后库存 " + stockAfter + " 件合法"
                            : "补后库存会超出上限（" + MAX_STOCK + "），请分批入库"));
            if (!rangeOk) {
                return fail("PARAM_INVALID", "补完库存会超过上限（" + MAX_STOCK + "），请分批入库。",
                        null, product.getId(), checks);
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", normalizedMode);
            params.put("delta", finalDelta);
            if ("TARGET".equals(normalizedMode)) {
                params.put("targetStock", targetStock);
            }
            params.put("note", "AI 对话补货");

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("productId", product.getId());
            snapshot.put("productName", product.getName());
            // 【全流程最关键的字段】执行时 Service 会用悲观写锁重读库存并与它比对
            snapshot.put("expectedStock", currentStock);
            snapshot.put("stockBefore", currentStock);
            snapshot.put("stockAfter", stockAfter);
            snapshot.put("delta", finalDelta);
            snapshot.put("unit", product.getUnit());
            snapshot.put("threshold", AdminService.LOW_STOCK_THRESHOLD);
            snapshot.put("sales", product.getSales());

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(field("商品", product.getName() + "（ID " + product.getId() + "，单位 "
                    + (product.getUnit() == null ? "件" : product.getUnit()) + "）"));
            fields.add(fieldTone("库存变化",
                    currentStock + " → " + stockAfter + "（+" + finalDelta + "）", "normal"));
            if (conversionNote != null) {
                fields.add(field("换算说明", conversionNote));
            }
            fields.add(field("安全阈值", AdminService.LOW_STOCK_THRESHOLD + " 件"
                    + (stockAfter > AdminService.LOW_STOCK_THRESHOLD ? "（补后已高于阈值，预警解除）" : "")));
            fields.add(field("累计销量", valueOr(product.getSales(), 0) + " 件"));
            fields.add(field("当前库存快照", currentStock + " 件（执行时会比对，变了就拒绝执行）"));

            List<String> riskReasons = new ArrayList<>();
            boolean bigAmount = finalDelta >= 1000 || stockAfter >= 5000;
            if (bigAmount) {
                riskReasons.add("补货量较大（+" + finalDelta + " 件，补后 " + stockAfter
                        + " 件），请确认是否为实际到货量");
            }
            Map<String, Object> risk = risk(bigAmount ? "MEDIUM" : "LOW", riskReasons, false, null, false,
                    "补货不做库存流水，追溯依赖操作日志");

            String targetLabel = "补货 · " + product.getName();
            return toJson(proposal(AdminAction.STOCK_REPLENISH, product.getId(), targetLabel,
                    params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  4. 新建优惠活动
    // =====================================================================

    @Tool(name = "prepare_promotion", value = """
            准备新建一个优惠活动。仅当用户明确要创建活动时调用。
            本工具只做预检并生成"待确认提案"，不执行创建、不改变下单价格；执行必须由用户点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已创建"。
            类目活动本期不支持：只能"全店通用"或"指定单个商品"，用户说"给生鲜类目做个活动"时必须澄清。
            活动名称可由你起草 2 个候选让用户选，但不得静默采用。""")
    public String preparePromotion(
            @ToolMemoryId String memoryKey,
            @P("活动名称，1-64 字") String name,
            @P("FULL_REDUCTION=满减 / DISCOUNT=折扣") String type,
            @P(value = "满减门槛（元），满减时必填，默认 0", required = false) String threshold,
            @P("满减金额（元）或折扣率（0.8=8折），必填") String discount,
            @P(value = "限定商品 ID；不传=全店通用", required = false) Long productId,
            @P(value = "开始时间 yyyy-MM-ddTHH:mm:ss（Asia/Shanghai）；不传=立即", required = false) String startTime,
            @P(value = "结束时间同上；不传=不限", required = false) String endTime,
            @P(value = "活动描述，≤512 字", required = false) String description) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            boolean perm = ctx.hasAnyPerm("promotion:manage");
            checks.add(check("PERMISSION", perm, perm ? "具备 promotion:manage" : "缺少「促销管理」权限"));
            if (!perm) {
                return fail("PERM_DENIED", "创建活动需要「促销管理」权限。", null, null, checks);
            }

            String normalizedName = trimToNull(name);
            if (normalizedName == null) {
                checks.add(check("PARAM", false, "缺少活动名称"));
                return fail("PARAM_MISSING", "活动叫什么名字？（会显示在 App 活动页）", null, null, checks);
            }
            if (normalizedName.length() > 64) {
                checks.add(check("PARAM", false, "活动名称超过 64 字"));
                return fail("PARAM_INVALID", "活动名称不能超过 64 个字。", null, null, checks);
            }

            PromotionType normalizedType = normalizePromotionType(type);
            if (normalizedType == null) {
                checks.add(check("PARAM", false, "活动类型无法识别"));
                return fail("PARAM_INVALID", "活动类型只支持满减或折扣。", null, null, checks);
            }

            BigDecimal thresholdValue = parseDecimal(threshold);
            if (thresholdValue == null) thresholdValue = BigDecimal.ZERO;
            BigDecimal discountValue = parseDecimal(discount);
            if (discountValue == null) {
                checks.add(check("PARAM", false, "缺少优惠值"));
                return fail("PARAM_MISSING", "满减金额或折扣率是多少？", null, null, checks);
            }

            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);
            if ((startTime != null && !startTime.isBlank()) && start == null) {
                checks.add(check("PARAM", false, "开始时间格式无法解析"));
                return fail("PARAM_INVALID", "开始时间格式不对，请用 yyyy-MM-ddTHH:mm:ss。", null, null, checks);
            }
            if ((endTime != null && !endTime.isBlank()) && end == null) {
                checks.add(check("PARAM", false, "结束时间格式无法解析"));
                return fail("PARAM_INVALID", "结束时间格式不对，请用 yyyy-MM-ddTHH:mm:ss。", null, null, checks);
            }

            // ---- 复用 PromotionService 的同一份校验（禁止在本类重写一套，否则两套规则必然漂移）
            PromotionRequest request = new PromotionRequest();
            request.setName(normalizedName);
            request.setType(normalizedType);
            request.setThreshold(thresholdValue);
            request.setDiscount(discountValue);
            request.setProductId(productId);
            request.setStartTime(start);
            request.setEndTime(end);
            request.setDescription(trimToNull(description));
            request.setStatus(1);
            try {
                promotionService.validateForPreview(request);
                checks.add(check("FIELD_VALID", true, "字段校验通过（与创建接口同一份实现）"));
            } catch (BusinessException e) {
                checks.add(check("FIELD_VALID", false, e.getMessage()));
                String subCode = e.getSubCode() == null ? "PARAM_INVALID" : e.getSubCode();
                return fail(subCode, e.getMessage(), null, null, checks);
            }

            // ---- 商品存在性
            String productName = null;
            if (productId != null && productId > 0) {
                Product product = productRepository.findById(productId).orElse(null);
                if (product == null) {
                    checks.add(check("PRODUCT", false, "指定的商品不存在"));
                    return fail("TARGET_NOT_FOUND", "指定的商品不存在，选全店还是换个商品？", null, null, checks);
                }
                productName = product.getName();
                checks.add(check("PRODUCT", true, "限定商品：" + productName));
            } else {
                checks.add(check("PRODUCT", true, "全店通用"));
            }

            // ---- 同源查重：命中只警告不拦截（同源活动结算时只算一次，不会叠加优惠）
            List<Promotion> duplicates = promotionService.findDuplicates(request);
            List<String> riskReasons = new ArrayList<>();
            String duplicateNote = null;
            if (!duplicates.isEmpty()) {
                Promotion first = duplicates.get(0);
                duplicateNote = "已有同源活动「" + first.getName() + "」在跑且时间重叠，结算时只算一次优惠";
                riskReasons.add(duplicateNote);
            }
            checks.add(check("DUPLICATE", true, duplicates.isEmpty()
                    ? "没有同源活动"
                    : "命中 " + duplicates.size() + " 个同源活动（仅提示，不拦截）"));

            // ---- 风险判定
            String riskLevel = "LOW";
            if (normalizedType == PromotionType.DISCOUNT) {
                if (discountValue.compareTo(new BigDecimal("0.5")) < 0) {
                    riskLevel = "HIGH";
                    riskReasons.add("折扣力度极大（" + discountValue.stripTrailingZeros().toPlainString()
                            + "，低于 5 折），请确认");
                }
            } else if (thresholdValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = discountValue.divide(thresholdValue, 4, RoundingMode.HALF_UP);
                if (ratio.compareTo(new BigDecimal("0.5")) > 0) {
                    riskLevel = "HIGH";
                    riskReasons.add("优惠占门槛比例过高（" + ratio.multiply(new BigDecimal("100"))
                            .setScale(0, RoundingMode.HALF_UP) + "%），请确认");
                }
                if (discountValue.compareTo(thresholdValue) >= 0) {
                    if ("LOW".equals(riskLevel)) riskLevel = "MEDIUM";
                    riskReasons.add("满 " + thresholdValue.stripTrailingZeros().toPlainString()
                            + " 减 " + discountValue.stripTrailingZeros().toPlainString()
                            + " 会让实付变成 0 元");
                }
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", normalizedName);
            params.put("type", normalizedType.name());
            params.put("threshold", thresholdValue.stripTrailingZeros().toPlainString());
            params.put("discount", discountValue.stripTrailingZeros().toPlainString());
            params.put("productId", productId);
            params.put("startTime", ts(start));
            params.put("endTime", ts(end));
            params.put("description", trimToNull(description));

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", normalizedName);
            snapshot.put("type", normalizedType.name());
            snapshot.put("threshold", thresholdValue.stripTrailingZeros().toPlainString());
            snapshot.put("discount", discountValue.stripTrailingZeros().toPlainString());
            snapshot.put("productId", productId);
            snapshot.put("startTime", ts(start));
            snapshot.put("endTime", ts(end));
            snapshot.put("duplicateCount", duplicates.size());
            snapshot.put("duplicates", duplicates.stream().map(AdminActionTools::duplicateLabel).toList());

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(field("活动名称", normalizedName));
            fields.add(field("优惠规则", readableRule(normalizedType, thresholdValue, discountValue)));
            fields.add(field("适用范围", productName == null ? "全店通用" : "指定商品：" + productName));
            fields.add(field("时间窗", (start == null ? "立即生效" : start.format(TS_FRIENDLY))
                    + " 至 " + (end == null ? "不限" : end.format(TS_FRIENDLY))
                    + (durationDays(start, end) > 0 ? "（" + durationDays(start, end) + " 天）" : "")));
            if (duplicateNote != null) {
                fields.add(fieldTone("同源冲突", duplicateNote, "warn"));
            }
            fields.add(field("生效影响", "创建后立即影响下单实付与 GMV 口径，不可自动撤销"));

            boolean second = !duplicates.isEmpty() || "HIGH".equals(riskLevel);
            Map<String, Object> risk = risk(riskLevel, riskReasons, second, null, false,
                    "活动一旦生效会立即影响下单价格");

            String targetLabel = "新建优惠活动 · " + normalizedName;
            // 活动尚未创建，没有主键：targetId=null，路径不含 {targetId} 占位符
            return toJson(proposal(AdminAction.PROMOTION_CREATE, null, targetLabel,
                    params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  5. 订单发货
    // =====================================================================

    @Tool(name = "prepare_shipment", value = """
            准备一次订单发货。仅当用户明确要执行发货时调用。
            本工具只做预检并生成"待确认提案"，不执行发货、不写物流记录；执行必须由用户点击确认卡片。
            调用后只能说"已准备好，请点击卡片确认"，绝不能说"已发货"。
            【绝对禁止】不得自行编造运单号，也不得"自动生成"运单号 —— 运单号必须由用户提供，原样回显在卡片上。
            用户没说运单号时，追问运单号，不要给卡片。""")
    public String prepareShipment(
            @ToolMemoryId String memoryKey,
            @P("订单号，形如 DR20261008120034123") String orderNo,
            @P("物流公司，如 顺丰速运 / 中通快递") String company,
            @P("运单号，6-32 位字母数字，必须由用户提供") String trackingNo,
            @P(value = "发货备注，≤255 字", required = false) String remark) {
        try {
            String identity = identityProblem(memoryKey);
            if (identity != null) {
                return fail(identity, IDENTITY_LOST, null, null, List.of());
            }
            AdminActionContext ctx = AdminActionContext.current().orElse(null);
            if (ctx == null) {
                return fail("CONTEXT_MISSING", IDENTITY_LOST, null, null, List.of());
            }

            List<Map<String, Object>> checks = new ArrayList<>();

            // 发货必须 order:ship；能用 order:view 看订单 ≠ 能发货
            boolean perm = ctx.hasAnyPerm("order:ship");
            checks.add(check("PERMISSION", perm, perm ? "具备 order:ship" : "缺少「订单发货」权限"));
            if (!perm) {
                return fail("PERM_DENIED", "发货需要「订单发货」权限。", null, null, checks);
            }

            if (orderNo == null || orderNo.isBlank()) {
                checks.add(check("PARAM", false, "缺少订单号"));
                return fail("PARAM_MISSING", "发哪一单？给我订单号。", null, null, checks);
            }
            String companyValue = trimToNull(company);
            if (companyValue == null) {
                checks.add(check("PARAM", false, "缺少物流公司"));
                return fail("PARAM_MISSING", "用哪家快递？", null, null, checks);
            }
            if (companyValue.length() > 32) {
                checks.add(check("PARAM", false, "物流公司名称过长"));
                return fail("PARAM_INVALID", "物流公司名称不能超过 32 个字。", null, null, checks);
            }
            // 运单号只 trim 首尾空白，之后原样使用：卡片上显示的 == 用户说的 == 最终写库的
            String trackingValue = trimToNull(trackingNo);
            if (trackingValue == null) {
                checks.add(check("PARAM", false, "缺少运单号"));
                return fail("PARAM_MISSING",
                        "还缺运单号 —— 运单号必须由你提供，我不会替你编一个。", null, null, checks);
            }
            checks.add(check("PARAM", true, "参数完整"));

            String no = orderNo.trim();
            Order order = orderRepository.findByOrderNo(no).orElse(null);
            if (order == null) {
                checks.add(check("TARGET", false, "订单不存在"));
                return fail("TARGET_NOT_FOUND", "没找到这个订单，核对订单号。", null, null, checks);
            }
            checks.add(check("TARGET", true, "订单 " + no + " 已定位"));

            boolean paid = order.getStatus() == OrderStatus.PAID;
            checks.add(check("ORDER_STATE", paid,
                    paid ? "订单待发货" : "订单当前是【" + order.getStatus().getLabel() + "】"));
            if (!paid) {
                return fail("ORDER_STATE_INVALID", shipStateMessage(order.getStatus()), null, order.getId(), checks);
            }

            boolean formatOk = TRACKING_NO.matcher(trackingValue).matches();
            checks.add(check("TRACKING_FORMAT", formatOk,
                    formatOk ? "运单号格式正确" : "运单号格式不对（6-32 位字母数字）"));
            if (!formatOk) {
                return fail("PARAM_INVALID", "运单号格式不对（6–32 位字母数字），再核对一下？",
                        null, order.getId(), checks);
            }

            // 运单号全库唯一（排除本单）：错填会让两单同号，客户永远收不到货
            boolean conflict = shipmentRepository.existsByTrackingNoAndOrderIdNot(trackingValue, order.getId());
            checks.add(check("TRACKING_UNIQUE", !conflict,
                    conflict ? "运单号已被其它订单占用" : "运单号未被占用"));
            if (conflict) {
                String usedBy = shipmentRepository.findFirstByTrackingNo(trackingValue)
                        .map(Shipment::getOrderNo).orElse("其它订单");
                return fail("TRACKING_NO_CONFLICT",
                        "这个运单号已经用在订单 " + usedBy + " 上了，确认是不是填错了？",
                        null, order.getId(), checks);
            }

            Shipment existing = shipmentRepository.findByOrderId(order.getId()).orElse(null);
            boolean duplicateShipment = existing != null && existing.getStatus() == ShipmentStatus.SHIPPED;
            checks.add(check("SHIPMENT_EXISTS", !duplicateShipment,
                    duplicateShipment ? "该单已有已发货的物流记录（运单号 " + existing.getTrackingNo() + "）"
                            : (existing != null ? "该单已有非发货物流记录，本次将覆盖运单号" : "无物流记录")));
            if (duplicateShipment) {
                return fail("DUPLICATE_SHIPMENT",
                        "这单已经发过了（运单号 " + existing.getTrackingNo() + "），不要重复发。",
                        null, order.getId(), checks);
            }

            int waited = waitedHours(order.getPaidAt());
            List<String> riskReasons = new ArrayList<>();
            if (waited > AdminService.SLA_SHIP_DAYS * 24) {
                riskReasons.add("超过 " + AdminService.SLA_SHIP_DAYS + " 天发货承诺（已等待 "
                        + humanDuration(waited) + "）");
            }
            String note = existing != null && existing.getTrackingNo() != null
                    ? "将覆盖原运单号 " + existing.getTrackingNo()
                    : null;

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("orderNo", no);
            params.put("company", companyValue);
            params.put("trackingNo", trackingValue);
            params.put("remark", trimToNull(remark));

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("orderNo", order.getOrderNo());
            snapshot.put("status", order.getStatus().name());
            snapshot.put("amount", money(order.getPayAmount()));
            snapshot.put("paidAt", ts(order.getPaidAt()));
            snapshot.put("waitedHours", waited);
            snapshot.put("userId", order.getUserId());
            snapshot.put("receiverName", order.getReceiverName());
            snapshot.put("existingTrackingNo", existing == null ? null : existing.getTrackingNo());

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(fieldMono("订单号", order.getOrderNo()));
            fields.add(field("买家", nicknameOf(order.getUserId())));
            fields.add(field("实付金额", "¥" + money(order.getPayAmount())));
            fields.add(field("付款时间", order.getPaidAt() == null ? "-" : order.getPaidAt().format(TS_FRIENDLY)));
            fields.add(fieldTone("已等待", humanDuration(waited)
                    + (waited > AdminService.SLA_SHIP_DAYS * 24 ? "（已超时）" : ""),
                    waited > AdminService.SLA_SHIP_DAYS * 24 ? "danger" : "normal"));
            fields.add(field("物流公司", companyValue));
            fields.add(fieldMono("运单号", trackingValue));
            String remarkValue = trimToNull(remark);
            if (remarkValue != null) {
                fields.add(field("备注", remarkValue));
            }
            fields.add(field("状态变化", "待发货 → 待收货"));

            boolean secondConfirm = waited > AdminService.SLA_SHIP_DAYS * 24;
            Map<String, Object> risk = risk(secondConfirm ? "MEDIUM" : "LOW", riskReasons,
                    false, null, true, note);

            String targetLabel = "确认发货 · " + order.getOrderNo();
            return toJson(proposal(AdminAction.ORDER_SHIP, order.getId(), targetLabel,
                    params, checks, snapshot, fields, risk));
        } catch (Exception e) {
            return fail("DOWNSTREAM_ERROR", "预检失败：" + safeMessage(e), null, null, List.of());
        }
    }

    // =====================================================================
    //  身份预检（模型无法伪造身份，见类注释）
    // =====================================================================

    /**
     * 交叉校验身份：{@link AdminActionContext}（Controller 绑定）与 {@code @ToolMemoryId}（框架注入）。
     *
     * @return {@code null} 表示通过；否则返回 subCode（{@code CONTEXT_MISSING} / {@code OPERATOR_MISMATCH}）
     */
    private static String identityProblem(String memoryKey) {
        AdminActionContext ctx = AdminActionContext.current().orElse(null);
        if (ctx == null || ctx.operatorId() == null) {
            // 上下文丢失（例如工具被框架调度到别的线程）：拒绝执行，绝不降级跳过权限
            return "CONTEXT_MISSING";
        }
        Long memoryUserId = JpaChatMemoryStore.resolveUserId(memoryKey);
        if (memoryUserId != null && !memoryUserId.equals(ctx.operatorId())) {
            // 两个管理员并发、memoryKey 串了：宁可让用户重试一次，也不能错人操作
            return "OPERATOR_MISMATCH";
        }
        return null;
    }

    // =====================================================================
    //  结果组装
    // =====================================================================

    /** 成功素材：{@code ok/action/targetId/params/precheck/snapshot/fields/risk} 八件套。 */
    private static Map<String, Object> proposal(AdminAction action,
                                                Long targetId,
                                                String targetLabel,
                                                Map<String, Object> params,
                                                List<Map<String, Object>> checks,
                                                Map<String, Object> snapshot,
                                                List<Map<String, Object>> fields,
                                                Map<String, Object> risk) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", action.name());
        out.put("targetType", action.targetType().name());
        out.put("targetId", targetId);
        out.put("targetLabel", targetLabel);
        out.put("params", params);
        out.put("precheck", precheck(checks));
        out.put("snapshot", snapshot);
        out.put("fields", fields);
        out.put("risk", risk);
        // 提示模型该怎么说话（避免它自作主张宣称"已完成"）
        out.put("sayAfter", "已准备好" + action.label() + "提案，请点击卡片确认后再执行。");
        return out;
    }

    private static Map<String, Object> precheck(List<Map<String, Object>> checks) {
        boolean ok = true;
        for (Map<String, Object> c : checks) {
            if (!Boolean.TRUE.equals(c.get("ok"))) {
                ok = false;
                break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("checkedAt", LocalDateTime.now().format(TS));
        out.put("checks", checks);
        return out;
    }

    private static Map<String, Object> check(String code, boolean ok, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("ok", ok);
        out.put("message", message);
        return out;
    }

    private static Map<String, Object> field(String label, String value) {
        return fieldTone(label, value, "normal");
    }

    private static Map<String, Object> fieldTone(String label, String value, String tone) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", label);
        out.put("value", value);
        out.put("tone", tone);
        out.put("mono", false);
        return out;
    }

    private static Map<String, Object> fieldMono(String label, String value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", label);
        out.put("value", value);
        out.put("tone", "normal");
        out.put("mono", true);
        return out;
    }

    private static Map<String, Object> risk(String level,
                                            List<String> reasons,
                                            boolean requiresSecondConfirm,
                                            String secondConfirmHint,
                                            boolean irreversible,
                                            String note) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", level);
        out.put("reasons", reasons);
        out.put("requiresSecondConfirm", requiresSecondConfirm);
        out.put("secondConfirmHint", secondConfirmHint);
        out.put("irreversible", irreversible);
        out.put("note", note);
        return out;
    }

    /** 失败返回：{@code ok=false} + subCode + 人话；同时带上已算出的 checks，便于模型解释。 */
    private String fail(String subCode, String message, AdminAction action, Long targetId,
                        List<Map<String, Object>> checks) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("subCode", subCode);
        out.put("message", message);
        if (action != null) {
            out.put("action", action.name());
            out.put("targetType", action.targetType().name());
        }
        out.put("targetId", targetId);
        out.put("precheck", precheck(checks));
        return toJson(out);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"ok\":false,\"subCode\":\"DOWNSTREAM_ERROR\",\"message\":\"预检结果序列化失败，请重试一次\"}";
        }
    }

    // =====================================================================
    //  解析与格式化
    // =====================================================================

    /** 决策归一化：容忍中文（通过/同意/批 → APPROVE；拒绝/驳回/不同意 → REJECT）。 */
    private static String normalizeDecision(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase();
        if (s.contains("APPROVE") || s.contains("通过") || s.contains("同意")) return "APPROVE";
        if (s.contains("REJECT") || s.contains("拒") || s.contains("驳回")
                || s.contains("不同意") || s.contains("批不了")) return "REJECT";
        return null;
    }

    private static PromotionType normalizePromotionType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase();
        if (s.startsWith("FULL") || s.contains("满减") || s.contains("满")) return PromotionType.FULL_REDUCTION;
        if (s.startsWith("DISCOUNT") || s.contains("折扣") || s.contains("打折") || s.contains("折")) {
            return PromotionType.DISCOUNT;
        }
        return null;
    }

    /** 严格解析金额/折扣率：失败返回 null（禁止 double，避免金额精度漂移）。 */
    private static BigDecimal parseDecimal(String raw) {
        String s = trimToNull(raw);
        if (s == null) return null;
        // 容忍「8折」「85%」这类口语写法
        if (s.endsWith("折")) {
            String number = s.substring(0, s.length() - 1).trim();
            BigDecimal zhe = parseDecimal(number);
            if (zhe == null) return null;
            // 8 折 = 0.8；8.5 折 = 0.85
            return zhe.compareTo(BigDecimal.ONE) > 0
                    ? zhe.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP)
                    : zhe;
        }
        if (s.endsWith("%")) {
            String number = s.substring(0, s.length() - 1).trim();
            BigDecimal pct = parseDecimal(number);
            return pct == null ? null : pct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析 {@code yyyy-MM-ddTHH:mm:ss}，兼容 {@code yyyy-MM-dd HH:mm:ss} 与纯日期。 */
    private static LocalDateTime parseDateTime(String raw) {
        String s = trimToNull(raw);
        if (s == null) return null;
        String normalized = s.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignore) {
            // 落到「纯日期」再试一次
        }
        try {
            return LocalDateTime.parse(normalized + "T00:00:00");
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String ts(LocalDateTime time) {
        return time == null ? null : time.format(TS);
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 未读指纹里的条数。
     * <p>聚合查询的 {@code count} 不可能为 null，这里只是防御性取值 ——
     * 让「统计异常」退化成「0 条未读」而不是 NPE 打断整个预检。
     */
    private static long countOf(RiskScoreRepository.UnreadStat stat) {
        if (stat == null || stat.getCnt() == null) return 0L;
        return stat.getCnt();
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    /** 订单已等待小时数（以付款时间为起点；未付款时退化为下单时间）。 */
    private static int waitedHours(LocalDateTime paidAt) {
        if (paidAt == null) return 0;
        long minutes = Duration.between(paidAt, LocalDateTime.now()).toMinutes();
        return minutes <= 0 ? 0 : (int) (minutes / 60);
    }

    /** 「3 天 5 小时」这类人话时长。 */
    private static String humanDuration(int hours) {
        if (hours <= 0) return "不到 1 小时";
        int days = hours / 24;
        int rest = hours % 24;
        if (days <= 0) return rest + " 小时";
        return days + " 天" + (rest > 0 ? " " + rest + " 小时" : "");
    }

    private String nicknameOf(Long userId) {
        if (userId == null) return "未知买家";
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return "用户 #" + userId;
        String nickname = user.getNickname();
        return (nickname == null || nickname.isBlank()) ? user.getUsername() : nickname;
    }

    /** 订单商品总件数（卡片上「等 N 件」的口径）。 */
    private int countItems(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items == null || items.isEmpty()) return 0;
        int total = 0;
        for (OrderItem item : items) {
            total += valueOr(item.getQuantity(), 0);
        }
        return total;
    }

    /** 退款被拒后订单会回到哪个状态（与 RefundService.reject 的兜底一致：previousStatus，缺省 PAID）。 */
    private static String previousStatusLabel(RefundRequest refund) {
        OrderStatus previous = refund.getPreviousStatus();
        return previous == null ? OrderStatus.PAID.getLabel() : previous.getLabel();
    }

    /** 非 PAID 发货的具体话术（PRD A5-3 要求每种状态给一句不同的话）。 */
    private static String shipStateMessage(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> "这单还没付款，不能发货。";
            case SHIPPED -> "这单已经发过了，不要重复发。";
            case COMPLETED -> "这单已经完成，不需要再发货。";
            case REFUNDING -> "这单在退款中，先处理退款再决定发不发。";
            case CANCELLED -> "订单已取消，不能发货。";
            case REFUNDED -> "订单已退款，不能发货。";
            default -> "仅待发货订单可发货（当前：" + status.getLabel() + "）。";
        };
    }

    /** 活动规则的「人话」换算：满 ¥200 减 ¥30（相当于 85 折）。 */
    private static String readableRule(PromotionType type, BigDecimal threshold, BigDecimal discount) {
        if (type == PromotionType.DISCOUNT) {
            BigDecimal zhe = discount.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);
            String prefix = threshold.compareTo(BigDecimal.ZERO) > 0
                    ? "满 ¥" + threshold.stripTrailingZeros().toPlainString() + " 打 "
                    : "全场打 ";
            return prefix + zhe.stripTrailingZeros().toPlainString() + " 折";
        }
        String base = "满 ¥" + threshold.stripTrailingZeros().toPlainString()
                + " 减 ¥" + discount.stripTrailingZeros().toPlainString();
        if (threshold.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payRate = BigDecimal.ONE.subtract(
                    discount.divide(threshold, 4, RoundingMode.HALF_UP));
            if (payRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal zhe = payRate.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);
                base += "（相当于 " + zhe.stripTrailingZeros().toPlainString() + " 折）";
            } else {
                base += "（实付会变成 0 元）";
            }
        }
        return base;
    }

    private static long durationDays(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        long days = Duration.between(start, end).toDays();
        return days < 0 ? 0 : days;
    }

    private static String duplicateLabel(Promotion p) {
        return "「" + p.getName() + "」（"
                + (p.getStartTime() == null ? "不限" : p.getStartTime().format(TS_FRIENDLY))
                + " ~ " + (p.getEndTime() == null ? "不限" : p.getEndTime().format(TS_FRIENDLY)) + "）";
    }
}
