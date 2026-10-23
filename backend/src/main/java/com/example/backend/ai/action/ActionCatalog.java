package com.example.backend.ai.action;

import com.example.backend.dto.ActionProposal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 动作白名单：{@code action → 接口/权限/幂等键/二次确认阈值} 的<b>唯一</b>映射表。
 *
 * <h3>这个类为什么被称为「安全命门」</h3>
 * 模型只会说「我要做 {@code REFUND_APPROVE}，对象是 8，参数是这样」。
 * 「到底打哪个 URL、用什么 method、body 里放什么」全部由本类渲染。于是：
 * <ul>
 *   <li>模型输出的 JSON 里<b>没有 path 字段</b>，注入成功也无处可写；</li>
 *   <li>{@code targetId} 只能内插进模板里的 {@code {targetId}} 占位符，
 *       模板里<b>不允许</b>出现 {@code {params.xxx}}（构造时强制校验），
 *       因此模型无法通过参数影响路径；</li>
 *   <li>{@code bodyBuilder} 只取白名单字段 —— 模型多塞一个 {@code orderId} 也进不了 body。</li>
 * </ul>
 *
 * <h3>未注册动作必须抛异常，绝不返回 null</h3>
 * 返回 null 会让上层误当成「无 confirm 的卡片」渲染出去 —— 那就是漏洞。
 * 本类 {@link #endpoint(AdminAction)} 对未注册动作抛 {@link IllegalArgumentException}，
 * 由 {@code AdminActionService} 捕获后<b>丢弃该条提案</b>并在答复里提示「不支持该操作」。
 *
 * <h3>P1 批量动作为什么不注册</h3>
 * 批量的执行路径是「逐条 {@code item.targetId}」，无法用单个 {@code {targetId}} 模板表达。
 * 一旦注册，{@link #renderConfirm} 就会给出一张「指向单条路径却代表整批」的卡片 —— 那是错的。
 * 批量执行留到 P1 由执行器逐条渲染，本批刻意不注册（同时让 R-b 单动作关闭策略天然可用：
 * 从映射表移除即等于关闭）。
 */
@Component
public class ActionCatalog {

    /** 提案默认有效期（PRD Q5 拍板 5 分钟）。 */
    public static final Duration PROPOSAL_TTL = Duration.ofMinutes(5);

    /** 退款二次确认阈值：金额 ≥ ¥500。 */
    private static final BigDecimal SECOND_CONFIRM_REFUND_AMOUNT = new BigDecimal("500");

    /** 补货二次确认阈值：单次增量 ≥ 1000 件，或补后库存 ≥ 5000 件。 */
    private static final int SECOND_CONFIRM_RESTOCK_DELTA = 1000;
    private static final int SECOND_CONFIRM_STOCK_AFTER = 5000;

    /** 折扣类活动的二次确认阈值：折扣率 < 0.5（低于 5 折）。 */
    private static final BigDecimal SECOND_CONFIRM_DISCOUNT_RATE = new BigDecimal("0.5");

    /** 满减类活动的二次确认阈值：优惠/门槛 > 50%。 */
    private static final BigDecimal SECOND_CONFIRM_REDUCTION_RATIO = new BigDecimal("0.5");

    private final Map<AdminAction, Endpoint> endpoints;

    public ActionCatalog() {
        Map<AdminAction, Endpoint> map = new EnumMap<>(AdminAction.class);

        // ---------------- 退款：同意 / 拒绝 ----------------
        map.put(AdminAction.REFUND_APPROVE, new Endpoint(
                "POST",
                "/api/admin/refunds/{targetId}/approve",
                new String[]{"order:view", "cs:manage"},
                ctx -> "refund:" + requireTarget(ctx) + ":APPROVE",
                ctx -> null,
                ctx -> isRefundAmountLarge(ctx),
                true,
                "请输入订单号后 4 位以确认"));

        map.put(AdminAction.REFUND_REJECT, new Endpoint(
                "POST",
                "/api/admin/refunds/{targetId}/reject",
                new String[]{"order:view", "cs:manage"},
                ctx -> "refund:" + requireTarget(ctx) + ":REJECT",
                ActionCatalog::refundRejectBody,
                ctx -> isRefundAmountLarge(ctx),
                false,
                "请输入订单号后 4 位以确认"));

        // ---------------- 风险分析（单条） ----------------
        // 幂等键必须「对同一张卡片恒定」：执行器会用落库的 params/snapshot 重算并与前端
        // 传来的 Idempotency-Key 比对，因此任何「随当前时间变化」的键都会让卡片只要跨过
        // 一分钟确认就必然 PROPOSAL_MISMATCH（刷新后从「待确认操作」里再点也一样）。
        // 改为「订单 + 该单上次评分状态 + 是否重扫」的稳定指纹：
        //   · 同一张卡片连点两次 → 键相同 → 命中幂等，不会插两条评分；
        //   · 扫描完成后 lastScannedAt/currentScore 变化 → 新卡片得到新键 → 允许过一会儿重新评估。
        map.put(AdminAction.RISK_SCAN, new Endpoint(
                "POST",
                "/api/admin/risk/orders/{targetId}/scan",
                new String[]{"risk:scan"},
                ctx -> "risk:" + requireTarget(ctx)
                        + ":" + asString(snapshotValue(ctx, "lastScannedAt"))
                        + ":" + asString(snapshotValue(ctx, "currentScore"))
                        + ":" + asString(paramValue(ctx, "rescan")),
                ctx -> null,
                ctx -> false,
                false,
                null));

        // ---------------- 风险消息一键已读（批量，无单个 targetId） ----------------
        // 幂等键 = 「这一批未读的指纹」：未读条数 + 最大 id + 分数和。三种场景必须同值：
        //   · 同一张卡片连点两次 → 指纹相同 → 命中幂等，只跑一次 UPDATE、只写一条审计；
        //   · 期间又扫出新风险消息 / 运营手动读掉几条 → 指纹变化 → 新卡片拿到新键，
        //     不会出现「一键已读这辈子只能做一次」；
        //   · 未读归零时工具端直接返回 NO_UNREAD 不出卡，因此不存在空跑的执行。
        // 路径里没有任何占位符（作用对象是整个收件箱，不是某条记录），
        // 这正是它不需要走 P1「批量逐条」那套机制的原因。
        map.put(AdminAction.RISK_READ_ALL, new Endpoint(
                "PUT",
                "/api/admin/risk/scores/read-all",
                new String[]{"risk:view"},
                ctx -> "risk-read:" + asString(paramValue(ctx, "level"))
                        + ":" + asString(snapshotValue(ctx, "unreadCount"))
                        + ":" + asString(snapshotValue(ctx, "unreadMaxId"))
                        + ":" + asString(snapshotValue(ctx, "unreadScoreSum")),
                ActionCatalog::riskReadAllBody,
                ctx -> false,
                false,
                null));

        // ---------------- 补货 ----------------
        // 幂等键必须含 expectedStock：它是「这次补货基于哪份库存快照」的声明。
        // 7→107 与 107→207 是两次合法补货（键不同），而「同一张卡片连点两次」
        // 第二次键相同 → 命中幂等，不会重复入库。
        map.put(AdminAction.STOCK_REPLENISH, new Endpoint(
                "PUT",
                "/api/admin/products/{targetId}/stock",
                new String[]{"product:edit"},
                ctx -> "restock:" + requireTarget(ctx)
                        + ":" + asString(snapshotValue(ctx, "expectedStock"))
                        + ":" + asString(paramValue(ctx, "delta")),
                ActionCatalog::stockBody,
                ctx -> {
                    int delta = asInt(paramValue(ctx, "delta"), 0);
                    int stockAfter = asInt(snapshotValue(ctx, "stockAfter"), 0);
                    return delta >= SECOND_CONFIRM_RESTOCK_DELTA || stockAfter >= SECOND_CONFIRM_STOCK_AFTER;
                },
                false,
                "补货量较大，请输入商品 ID 以确认"));

        // ---------------- 新建活动 ----------------
        // 纯 INSERT，没有状态机兜底，因此幂等键必须是「内容指纹」：
        // 同内容重做本就不该做；合法重做（改了参数）必然产生新指纹。
        map.put(AdminAction.PROMOTION_CREATE, new Endpoint(
                "POST",
                "/api/admin/promotions",
                new String[]{"promotion:manage"},
                ctx -> "promotion:" + promotionDigest(ctx),
                ActionCatalog::promotionBody,
                ActionCatalog::isPromotionRisky,
                false,
                "活动参数非常规，请核对后勾选「我已核对」再确认"));

        // ---------------- 发货 ----------------
        map.put(AdminAction.ORDER_SHIP, new Endpoint(
                "PUT",
                "/api/admin/orders/{targetId}/ship",
                new String[]{"order:ship"},
                ctx -> "ship:" + requireTarget(ctx),
                ActionCatalog::shipBody,
                ctx -> false,
                true,
                null));

        validateTemplates(map);
        this.endpoints = Map.copyOf(map);
    }

    // =====================================================================
    //  渲染
    // =====================================================================

    /**
     * 渲染一条正向执行指令。
     *
     * @throws IllegalArgumentException 动作未注册，或 path 需要 {@code targetId} 但未提供合法值
     */
    public ActionProposal.Confirm renderConfirm(AdminAction action,
                                                Long targetId,
                                                Map<String, Object> params,
                                                Map<String, Object> snapshot) {
        Endpoint ep = endpoint(action);   // 未注册 → 抛异常（本类的核心契约）
        Ctx ctx = new Ctx(targetId, safe(params), safe(snapshot));

        String path = renderPath(ep, action, targetId);
        String idempotencyKey = ep.idempotencyKey().apply(ctx);
        Object body = ep.bodyBuilder() == null ? null : ep.bodyBuilder().apply(ctx);

        Map<String, String> headers;
        if (body == null && action != AdminAction.STOCK_REPLENISH) {
            headers = Map.of("Idempotency-Key", idempotencyKey);
        } else {
            headers = Map.of("Idempotency-Key", idempotencyKey, "Content-Type", "application/json");
        }
        return new ActionProposal.Confirm(ep.httpMethod(), path, body, headers);
    }

    /**
     * 渲染「反向操作」（仅退款：同意 ↔ 拒绝）。
     * <p>
     * 同一次预检已经把退款单、订单、权限都读完了，反向操作没有任何新的前置条件，
     * 因此可以一并渲染给前端，用户点「改为拒绝」时无需再走一轮对话。
     *
     * @return 反向操作指令；非退款动作返回 null
     */
    public ActionProposal.Confirm renderReverse(AdminAction action,
                                                Long targetId,
                                                Map<String, Object> params,
                                                Map<String, Object> snapshot) {
        AdminAction reverseAction = switch (action) {
            case REFUND_APPROVE -> AdminAction.REFUND_REJECT;
            case REFUND_REJECT -> AdminAction.REFUND_APPROVE;
            default -> null;
        };
        if (reverseAction == null) return null;

        Map<String, Object> reverseParams = new LinkedHashMap<>();
        reverseParams.put("decision", reverseAction == AdminAction.REFUND_APPROVE ? "APPROVE" : "REJECT");
        if (reverseAction == AdminAction.REFUND_REJECT) {
            reverseParams.put("rejectReason", defaultRejectReason(safe(params).get("rejectReason")));
        } else {
            reverseParams.put("rejectReason", null);
        }
        return renderConfirm(reverseAction, targetId, reverseParams, snapshot);
    }

    /** 幂等键（与 {@link ActionProposal#idempotencyKey()} 同源，执行器复核时可复用）。 */
    public String renderIdempotencyKey(AdminAction action,
                                       Long targetId,
                                       Map<String, Object> params,
                                       Map<String, Object> snapshot) {
        return endpoint(action).idempotencyKey().apply(new Ctx(targetId, safe(params), safe(snapshot)));
    }

    /** 是否需要二次确认（由服务端阈值判定，绝不采信模型自述）。 */
    public boolean requiresSecondConfirm(AdminAction action,
                                         Long targetId,
                                         Map<String, Object> params,
                                         Map<String, Object> snapshot) {
        Predicate<Ctx> p = endpoint(action).secondConfirm();
        return p != null && p.test(new Ctx(targetId, safe(params), safe(snapshot)));
    }

    /** 二次确认提示语（与动作绑定，前端原样展示）。 */
    public String secondConfirmHint(AdminAction action) {
        return endpoint(action).secondConfirmHint();
    }

    /** 是否不可撤销（退款/发货为 true，前端用危险色）。 */
    public boolean isIrreversible(AdminAction action) {
        return endpoint(action).irreversible();
    }

    /** 该动作需要的权限码（OR 语义）。 */
    public String[] permissionCodes(AdminAction action) {
        String[] codes = endpoint(action).permissionCodes();
        return codes == null ? new String[0] : codes.clone();
    }

    /** 权限判定（OR 语义；纯看权限码，不认角色，见 {@code AdminActionContext} 注释）。 */
    public boolean hasPermission(AdminAction action, Set<String> perms) {
        String[] codes = permissionCodes(action);
        if (perms == null || perms.isEmpty() || codes.length == 0) return false;
        for (String code : codes) {
            if (perms.contains(code)) return true;
        }
        return false;
    }

    /** 动作是否已注册（= 是否允许出现在卡片上）。 */
    public boolean isRegistered(AdminAction action) {
        return action != null && endpoints.containsKey(action);
    }

    /** 已注册动作集合（只读，供审计页/自测断言）。 */
    public Set<AdminAction> registeredActions() {
        return endpoints.keySet();
    }

    // =====================================================================
    //  摘要工具（params_digest 与活动内容指纹共用同一套规范化，避免两处漂移）
    // =====================================================================

    /**
     * 参数指纹：key 按字典序排序 + 值转字符串 + {@code |} 连接 + sha1。
     * <p>
     * 为什么不能直接比较 JSON 字符串：{@code {"a":1,"b":2}} 与 {@code {"b":2,"a":1}}
     * 语义相同但字符串不同，会导致「运营什么都没改却被判 PROPOSAL_MISMATCH」。
     */
    public static String paramsDigest(Map<String, Object> params) {
        return sha1Hex(canonical(safe(params)));
    }

    /**
     * 规范化：key 字典序 + {@code k=v} 用 {@code |} 连接。
     * 值为 null 时输出 {@code k=}（与「空串」区分不开，但 params 里空串无业务语义，实践中无歧义）。
     */
    public static String canonical(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        map.keySet().stream().sorted().forEach(k -> {
            if (sb.length() > 0) sb.append('|');
            sb.append(k).append('=').append(normalizeValue(map.get(k)));
        });
        return sb.toString();
    }

    /** 数值统一去尾零（{@code 200.00 → 200}），避免 {@code 0.80} 与 {@code 0.8} 被判成不同内容。 */
    private static String normalizeValue(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        if (v instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        String s = v.toString().trim();
        try {
            return new BigDecimal(s).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignore) {
            return s;
        }
    }

    /** sha1 十六进制（小写）。 */
    public static String sha1Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw == null ? new byte[0] : raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然内置 SHA-1；走到这里说明运行环境被裁剪，直接暴露为运行时错误
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }

    // =====================================================================
    //  内部：映射表结构
    // =====================================================================

    /**
     * 一次渲染所需的入参集合。
     *
     * @param targetId 对象主键（新建类动作可为 null）
     * @param params   模型给出的执行参数（只读）
     * @param snapshot 预检时刻的数据快照（只读）
     */
    public record Ctx(Long targetId, Map<String, Object> params, Map<String, Object> snapshot) {
    }

    /**
     * 一个动作的接口模板。
     *
     * @param httpMethod        HTTP 方法
     * @param pathTemplate      路径模板；可含 {@code {targetId}} 占位符，<b>不得</b>含 {@code {params.*}}
     * @param permissionCodes   权限码（OR 语义）
     * @param idempotencyKey    幂等键渲染函数
     * @param bodyBuilder       执行时 body（返回 null 表示无 body）
     * @param secondConfirm     是否要求二次确认
     * @param irreversible      是否不可撤销
     * @param secondConfirmHint 二次确认提示语
     */
    public record Endpoint(String httpMethod,
                           String pathTemplate,
                           String[] permissionCodes,
                           Function<Ctx, String> idempotencyKey,
                           Function<Ctx, Object> bodyBuilder,
                           Predicate<Ctx> secondConfirm,
                           boolean irreversible,
                           String secondConfirmHint) {
    }

    /** 取映射表条目；未注册 → 抛异常（绝不返回 null）。 */
    public Endpoint endpoint(AdminAction action) {
        if (action == null) {
            throw new IllegalArgumentException("未注册的动作: null");
        }
        Endpoint ep = endpoints.get(action);
        if (ep == null) {
            throw new IllegalArgumentException("未注册的动作: " + action.name());
        }
        return ep;
    }

    // =====================================================================
    //  内部：渲染细节
    // =====================================================================

    private static String renderPath(Endpoint ep, AdminAction action, Long targetId) {
        String template = ep.pathTemplate();
        if (!template.contains("{targetId}")) {
            // 例如 PROMOTION_CREATE：对象尚未创建，路径里没有任何主键
            return template;
        }
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException(
                    "动作 " + action.name() + " 需要合法的 targetId，实际为: " + targetId);
        }
        return template.replace("{targetId}", String.valueOf(targetId));
    }

    /** body：订单发货，只取三个白名单字段（模型多塞的键一律丢弃）。 */
    private static Object shipBody(Ctx ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("company", asString(paramValue(ctx, "company")));
        body.put("trackingNo", asString(paramValue(ctx, "trackingNo")));
        String remark = asString(paramValue(ctx, "remark"));
        if (remark != null && !remark.isBlank()) {
            body.put("remark", remark);
        }
        return body;
    }

    /**
     * body：风险消息一键已读，只取 {@code level}（ALL / HIGH / MEDIUM / LOW），其余键丢弃。
     *
     * <p>为什么 range 走 body 而不走路径或 query：本类的构造期自检禁止路径内插参数
     *（{@code {params.*}} 一律拒绝），而 query 同样要拼进 pathTemplate，一并被禁。
     * 放进 body 是唯一不破坏「path 只由白名单渲染」这条铁律的传参方式。
     */
    private static Object riskReadAllBody(Ctx ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        String level = asString(paramValue(ctx, "level"));
        body.put("level", (level == null || level.isBlank()) ? "ALL" : level);
        return body;
    }

    /** body：补货，只取 delta 与 expectedStock；缺 expectedStock 时不下发（页面入口的老行为）。 */
    private static Object stockBody(Ctx ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", asInt(paramValue(ctx, "delta"), 0));
        Integer expectedStock = asNullableInt(snapshotValue(ctx, "expectedStock"));
        if (expectedStock == null) {
            expectedStock = asNullableInt(paramValue(ctx, "expectedStock"));
        }
        if (expectedStock != null) {
            body.put("expectedStock", expectedStock);
        }
        return body;
    }

    /** body：拒绝退款，只取 reason（缺省时不下发，由 Service 用默认文案）。 */
    private static Object refundRejectBody(Ctx ctx) {
        String reason = asString(paramValue(ctx, "rejectReason"));
        if (reason == null || reason.isBlank()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());
        return body;
    }

    /** body：新建活动，按 {@code PromotionRequest} 的字段白名单逐项取，其余键丢弃。 */
    private static Object promotionBody(Ctx ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", asString(paramValue(ctx, "name")));
        body.put("type", asString(paramValue(ctx, "type")));
        body.put("threshold", decimalString(paramValue(ctx, "threshold"), "0"));
        body.put("discount", decimalString(paramValue(ctx, "discount"), "0"));
        Long productId = asNullableLong(paramValue(ctx, "productId"));
        if (productId != null && productId > 0) {
            body.put("productId", productId);
        }
        String startTime = asString(paramValue(ctx, "startTime"));
        if (startTime != null && !startTime.isBlank()) {
            body.put("startTime", startTime);
        }
        String endTime = asString(paramValue(ctx, "endTime"));
        if (endTime != null && !endTime.isBlank()) {
            body.put("endTime", endTime);
        }
        body.put("status", 1);
        String description = asString(paramValue(ctx, "description"));
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        return body;
    }

    // =====================================================================
    //  内部：判定
    // =====================================================================

    private static boolean isRefundAmountLarge(Ctx ctx) {
        BigDecimal amount = asBigDecimal(snapshotValue(ctx, "payAmount"));
        return amount != null && amount.compareTo(SECOND_CONFIRM_REFUND_AMOUNT) >= 0;
    }

    private static boolean isPromotionRisky(Ctx ctx) {
        String type = asString(paramValue(ctx, "type"));
        BigDecimal discount = asBigDecimal(paramValue(ctx, "discount"));
        BigDecimal threshold = asBigDecimal(paramValue(ctx, "threshold"));
        if (type == null || discount == null) return false;
        if ("DISCOUNT".equalsIgnoreCase(type)) {
            return discount.compareTo(SECOND_CONFIRM_DISCOUNT_RATE) < 0;
        }
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) return false;
        return discount.divide(threshold, 4, java.math.RoundingMode.HALF_UP)
                .compareTo(SECOND_CONFIRM_REDUCTION_RATIO) > 0;
    }

    // =====================================================================
    //  内部：取值与解析
    // =====================================================================

    private static Long requireTarget(Ctx ctx) {
        if (ctx.targetId() == null || ctx.targetId() <= 0) {
            throw new IllegalArgumentException("幂等键需要 targetId，实际为: " + ctx.targetId());
        }
        return ctx.targetId();
    }

    private static String promotionDigest(Ctx ctx) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("name", asString(paramValue(ctx, "name")));
        fingerprint.put("type", asString(paramValue(ctx, "type")));
        fingerprint.put("threshold", decimalString(paramValue(ctx, "threshold"), "0"));
        fingerprint.put("discount", decimalString(paramValue(ctx, "discount"), "0"));
        fingerprint.put("productId", asNullableLong(paramValue(ctx, "productId")));
        fingerprint.put("startTime", asString(paramValue(ctx, "startTime")));
        fingerprint.put("endTime", asString(paramValue(ctx, "endTime")));
        return sha1Hex(canonical(fingerprint));
    }

    private static String defaultRejectReason(Object raw) {
        String s = asString(raw);
        return (s == null || s.isBlank()) ? "商家未通过退款审核" : s.trim();
    }

    private static Object paramValue(Ctx ctx, String key) {
        return ctx.params().get(key);
    }

    /**
     * 取「快照值」，快照里没有就回落到请求参数。
     *
     * <h3>为什么要回落（QA Q-1）</h3>
     * 同一个幂等键会在三种场景下被渲染，三处必须得到<b>完全一样</b>的字符串，否则幂等就失效：
     * <ol>
     *   <li><b>预览时</b>（对话卡片）：{@code expectedStock} 在 snapshot 里（工具读的库存快照），
     *       params 里可能没有它 —— 走快照分支；</li>
     *   <li><b>执行时（带提案）</b>：执行器用<b>落库行</b>的 params + snapshot 重算，同上；</li>
     *   <li><b>执行时（不带提案，只带 {@code Idempotency-Key}）</b>：没有提案就没有快照，
     *       但请求体里带了 {@code expectedStock}，它进了 params —— 必须走回落分支。</li>
     * </ol>
     * 修复前第 3 种场景渲染成 {@code restock:2:null:100}，与卡片上的
     * {@code restock:2:798:100} 对不上，于是「页面/脚本自己带键直连」这条合法路径
     * 反而永远 {@code PROPOSAL_MISMATCH}。
     *
     * <p>回落只影响「快照里没有的键」，因此对 {@code stockAfter}、{@code payAmount}
     * 这类「只存在于快照、请求参数里根本没有」的键没有任何行为变化。
     */
    private static Object snapshotValue(Ctx ctx, String key) {
        Object fromSnapshot = ctx.snapshot().get(key);
        return fromSnapshot != null ? fromSnapshot : ctx.params().get(key);
    }

    private static Map<String, Object> safe(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String decimalString(Object v, String fallback) {
        BigDecimal bd = asBigDecimal(v);
        return bd == null ? fallback : bd.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int asInt(Object v, int fallback) {
        Integer i = asNullableInt(v);
        return i == null ? fallback : i;
    }

    private static Integer asNullableInt(Object v) {
        BigDecimal bd = asBigDecimal(v);
        return bd == null ? null : bd.intValue();
    }

    private static Long asNullableLong(Object v) {
        BigDecimal bd = asBigDecimal(v);
        return bd == null ? null : bd.longValue();
    }

    // =====================================================================
    //  内部：构造期自检
    // =====================================================================

    /**
     * 构造期强制校验三条铁律（写错一条就是安全漏洞，因此宁可启动失败也不要带病上线）：
     * <ol>
     *   <li>路径必须以 {@code /api/} 开头（前端 {@code api()} 只做一次前缀剥离）；</li>
     *   <li>路径里<b>不允许</b>出现 {@code {params}} 之类的参数占位符 —— targetId 是唯一允许的占位符；</li>
     *   <li>幂等键渲染函数必须存在（幂等是执行安全的底线）。</li>
     * </ol>
     */
    private static void validateTemplates(Map<AdminAction, Endpoint> map) {
        for (Map.Entry<AdminAction, Endpoint> e : map.entrySet()) {
            AdminAction action = e.getKey();
            Endpoint ep = e.getValue();
            String path = ep.pathTemplate();
            if (path == null || !path.startsWith("/api/")) {
                throw new IllegalStateException("动作 " + action + " 的路径必须以 /api/ 开头: " + path);
            }
            String lowered = path.toLowerCase();
            if (lowered.contains("{params") || lowered.contains("{snapshot") || lowered.contains("{body")) {
                throw new IllegalStateException("动作 " + action + " 的路径不允许内插参数: " + path);
            }
            if (ep.httpMethod() == null || ep.httpMethod().isBlank()) {
                throw new IllegalStateException("动作 " + action + " 缺少 httpMethod");
            }
            if (ep.idempotencyKey() == null) {
                throw new IllegalStateException("动作 " + action + " 缺少幂等键渲染函数");
            }
            if (ep.permissionCodes() == null || ep.permissionCodes().length == 0) {
                throw new IllegalStateException("动作 " + action + " 缺少权限码");
            }
        }
    }
}
