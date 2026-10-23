package com.example.backend.ai.action;

/**
 * 后台写操作的动作白名单。
 *
 * <h3>为什么必须先有枚举，再有工具</h3>
 * 模型只被允许说出「我要做哪个动作」（本枚举的一个值），<b>不允许说出「打哪个接口」</b>。
 * 动作 → 接口的映射全部落在 {@link ActionCatalog}，模型返回的 JSON 里连「路径」这个字段都没有，
 * 于是「提示词注入让模型拼出 {@code DELETE /api/admin/products/1}」在结构上无从发生。
 *
 * <h3>命名纪律</h3>
 * 枚举值一律「名词 + 动词过去式/名词化」的风格（{@code REFUND_APPROVE}、{@code STOCK_REPLENISH}），
 * 不写 {@code DO_REFUND} 这类祈使句 —— 它出现在日志、审计表、前端卡片标题里，
 * 读起来必须是「一件事」，而不是「一条命令」。
 */
public enum AdminAction {

    /** 同意退款（顺序：PENDING → APPROVED，订单 REFUNDING → REFUNDED，钱/库存/券一并回滚）。 */
    REFUND_APPROVE(TargetType.REFUND_REQUEST, "同意退款"),

    /** 拒绝退款（订单恢复到申请前状态，不回补库存、不返券）。 */
    REFUND_REJECT(TargetType.REFUND_REQUEST, "拒绝退款"),

    /** 单条订单风险分析（只打分，不改订单主状态）。 */
    RISK_SCAN(TargetType.ORDER, "风险分析"),

    /**
     * 风险消息一键已读（把 {@code risk_scores} 里未读的评分批量置为已读）。
     *
     * <p>这是本目录里<b>唯一一个「没有单个 targetId」的批量动作</b>，却没有落进
     * {@link #RISK_SCAN_BATCH} 那类 P1 占位的原因：它的作用对象是「整个收件箱」而不是
     * 「一组订单」，因此路径里天然不需要主键（{@code PUT /api/admin/risk/scores/read-all}），
     * 单个 {@code {targetId}} 模板足够表达 —— 这里干脆一个占位符都不用。
     */
    RISK_READ_ALL(TargetType.RISK_INBOX, "风险消息已读"),

    /** 商品补货（增加库存；带 {@code expectedStock} 快照防重复入库）。 */
    STOCK_REPLENISH(TargetType.PRODUCT, "补货"),

    /** 新建优惠活动（纯 INSERT，靠内容指纹幂等）。 */
    PROMOTION_CREATE(TargetType.PROMOTION, "新建优惠活动"),

    /** 订单发货（仅 PAID 可发；运单号必须由用户提供，模型不得编造）。 */
    ORDER_SHIP(TargetType.ORDER, "发货"),

    // ------------------------------------------------------------------
    //  以下为 P1 占位：本期工具端不生成，ActionCatalog 也<b>刻意不注册</b>。
    //  不注册的理由：批量动作的路径是「逐条 item.targetId」，无法用单个
    //  {targetId} 模板表达；一旦注册，renderConfirm 就会给出一张指向单条路径
    //  却代表整批的卡片 —— 那是错的。批量执行留到 P1 由执行器逐条渲染。
    // ------------------------------------------------------------------

    /** 批量风险分析（P1）。 */
    RISK_SCAN_BATCH(TargetType.ORDER_BATCH, "批量风险分析"),

    /** 批量发货（P1）。 */
    ORDER_SHIP_BATCH(TargetType.ORDER_BATCH, "批量发货");

    /**
     * 动作作用的对象类型。
     * <p>落库到 {@code admin_action_logs.target_type}，也是前端卡片「对象区」的分组依据。
     */
    public enum TargetType {
        REFUND_REQUEST,
        ORDER,
        PRODUCT,
        PROMOTION,
        ORDER_BATCH,
        /** 风险监控的「消息收件箱」（批量，无单个对象主键）。 */
        RISK_INBOX
    }

    private final TargetType targetType;
    private final String label;

    AdminAction(TargetType targetType, String label) {
        this.targetType = targetType;
        this.label = label;
    }

    public TargetType targetType() {
        return targetType;
    }

    /** 中文标签（前端卡片标题直接用，避免前端再维护一份中文映射）。 */
    public String label() {
        return label;
    }

    /** 动作码（= 枚举名，落库与对外 JSON 都用它）。 */
    public String code() {
        return name();
    }

    /**
     * 容错解析：支持枚举名与中文标签（模型有时会把中文标签回填进 action 字段）。
     *
     * @return 命中返回枚举值；无法识别返回 {@code null}（调用方必须当作「未注册动作」拒绝）
     */
    public static AdminAction tryParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (AdminAction a : values()) {
            if (a.name().equalsIgnoreCase(s) || a.label.equals(s)) return a;
        }
        return null;
    }
}
