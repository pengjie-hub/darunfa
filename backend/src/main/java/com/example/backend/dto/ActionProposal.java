package com.example.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 一条「待确认提案」——前端确认卡片的<b>唯一数据来源</b>。
 *
 * <h3>为什么卡片字段全部由服务端算好</h3>
 * PRD 要求卡片展示「商品摘要 / 余额支付部分 / SLE 已等待时长 / 库存 7→107 / 满减换算 85 折」
 * 等十余项<b>需要业务计算</b>的内容。若交给前端拼装，前端就得重复实现「SLA 2 天」
 * 「折扣换算」「商品摘要前 3 件 + 等 N 件」，一定会和后台口径漂移。
 * 因此 {@link #fields} 是服务端算好的 {@code {label,value,tone}} 列表，前端只做
 * 「渲染一堆键值行」，<b>零业务判断</b>。这也让「卡片内容只来自服务端」这条安全要求变得可执行。
 *
 * <h3>安全命门：{@link #confirm}</h3>
 * {@code confirm.method/path/body} 由服务端 {@code ActionCatalog} 用白名单模板渲染，
 * <b>模型输出的 JSON 里没有「路径」这个字段</b>。即使提示词注入成功，模型也无处可写 ——
 * 它只能说「我要做哪个动作、对哪个对象、用什么参数」。
 *
 * @param proposalId     服务端生成的提案 ID（{@code pa_} + 10 位 hex），同时是审计主键
 * @param action         动作枚举名（{@code AdminAction.name()}）
 * @param actionLabel    动作中文标签（前端标题直接用，避免前端维护中文映射）
 * @param targetType     对象类型（{@code AdminAction.TargetType.name()}）
 * @param targetId       对象主键；<b>唯一键定位，绝不用金额/名称</b>；新建类动作（活动）为 null
 * @param targetLabel    卡片标题副行，如「退款单 #8 · DR2026… · ¥199.00」
 * @param params         执行参数（与落库提案一致，用于 {@code params_digest} 校验）
 * @param precheck       预检结论（不通过的项前端置顶红字）
 * @param risk           风险提示（决定卡片配色与是否需要二次确认）
 * @param idempotencyKey 幂等键，确认请求时放进 {@code Idempotency-Key} 头
 * @param expiresAt      有效期，ISO-8601 带时区偏移（如 {@code 2026-10-22T14:35:12+08:00}）
 * @param snapshot       预检时刻的关键数据快照（补货的 {@code expectedStock} 就在这里）
 * @param confirm        正向操作的执行指令，由 {@code ActionCatalog} 渲染
 * @param reject         反向操作（仅退款：同一动作的对立面），可为 null
 * @param fields         卡片展示用的结构化字段（服务端算好，前端只渲染）
 */
public record ActionProposal(
        String proposalId,
        String action,
        String actionLabel,
        String targetType,
        Long targetId,
        String targetLabel,
        Map<String, Object> params,
        Precheck precheck,
        Risk risk,
        String idempotencyKey,
        String expiresAt,
        Map<String, Object> snapshot,
        Confirm confirm,
        Confirm reject,
        List<CardField> fields) {

    /**
     * 预检结论。
     *
     * @param ok        是否全部通过（不通过时<b>不生成提案</b>，只把原因作为对话文本返回）
     * @param checkedAt 预检时刻（{@code yyyy-MM-dd'T'HH:mm:ss}）
     * @param checks    逐项结论，按「越根本越前」排序：CONTEXT → PERMISSION → PARAM → TARGET → STATE → BUSINESS → RISK
     */
    public record Precheck(boolean ok, String checkedAt, List<Check> checks) {

        /** 空结论（防御性构造，避免 null 传播到前端）。 */
        public static Precheck empty() {
            return new Precheck(false, null, List.of());
        }
    }

    /**
     * 单项预检结论。
     *
     * @param code    检查项编码，如 {@code PERMISSION} / {@code REFUND_PENDING}
     * @param ok      是否通过
     * @param message 面向运营的中文说明（失败时是「为什么不能做」，成功时是「已核对什么」）
     */
    public record Check(String code, boolean ok, String message) {
    }

    /**
     * 风险提示。
     *
     * @param level                LOW / MEDIUM / HIGH
     * @param reasons              具体原因，前端渲染成黄条（HIGH 渲染成红条）
     * @param requiresSecondConfirm 是否需要卡片内联二次确认
     * @param secondConfirmHint    二次确认的提示语，如「请输入订单号后 4 位以确认」
     * @param irreversible         是否不可撤销（退款/发货为 true）
     * @param note                 补充说明（如「将覆盖原运单号 X」）
     */
    public record Risk(String level,
                       List<String> reasons,
                       boolean requiresSecondConfirm,
                       String secondConfirmHint,
                       boolean irreversible,
                       String note) {

        /** 无风险占位。 */
        public static Risk none(boolean irreversible) {
            return new Risk("LOW", List.of(), false, null, irreversible, null);
        }
    }

    /**
     * 执行指令（服务端渲染，模型无权输出）。
     *
     * @param method          HTTP 方法
     * @param path            完整路径，<b>必以 {@code /api} 开头</b>（前端只做一次前缀剥离）
     * @param body            请求体；null 表示无 body
     * @param requiresHeader  必须随请求带出的头（至少含 {@code Idempotency-Key}）
     */
    public record Confirm(String method, String path, Object body, Map<String, String> requiresHeader) {

        /** 空指令（永远不会被渲染出去，仅用于防御性判空）。 */
        public static Confirm empty() {
            return new Confirm(null, null, null, Map.of());
        }
    }

    /**
     * 卡片上的一行。
     *
     * @param label 字段名（如「退款金额」）
     * @param value 已格式化的值（如「¥199.00」「退款中 → 已退款」）
     * @param tone  配色：{@code normal} / {@code danger} / {@code warn}
     * @param mono  是否等宽字体（运单号、单号类字段）
     */
    public record CardField(String label, String value, String tone, boolean mono) {

        /** 普通配色、非等宽的便捷构造。 */
        public static CardField of(String label, String value) {
            return new CardField(label, value, "normal", false);
        }

        /** 强调（危险）配色的便捷构造。 */
        public static CardField danger(String label, String value) {
            return new CardField(label, value, "danger", false);
        }

        /** 警告配色的便捷构造。 */
        public static CardField warn(String label, String value) {
            return new CardField(label, value, "warn", false);
        }

        /** 等宽配色的便捷构造（运单号、订单号）。 */
        public static CardField mono(String label, String value) {
            return new CardField(label, value, "normal", true);
        }
    }
}
