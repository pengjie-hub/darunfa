package com.example.backend.service;

import com.example.backend.ai.action.ActionCatalog;
import com.example.backend.ai.action.AdminAction;
import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.ActionProposal;
import com.example.backend.entity.AdminActionLog;
import com.example.backend.repository.AdminActionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提案编译层（L2）：把 {@code prepare_*} 工具的返回串编译成「待确认提案」。
 *
 * <h3>它与工具的分工</h3>
 * 工具只负责「读库 + 预检 + 告诉模型该说什么」，返回的 JSON 里有 {@code action/targetId/params}
 * 但<b>没有 path</b>。本层做四件事：
 * <ol>
 *   <li><b>白名单校验</b>：{@code action} 必须是 {@link AdminAction} 且已注册在
 *       {@link ActionCatalog}，否则丢弃该条（{@code IllegalArgumentException}）；</li>
 *   <li><b>渲染 confirm</b>：method / path / body / 幂等键全部由 {@code ActionCatalog} 产出；</li>
 *   <li><b>算有效期</b>：{@code now + 5min}（数据会变，5 分钟后必须重新预检）；</li>
 *   <li><b>落库 PROPOSED</b>：审计链的第一环 —— 它同时回答了「AI 建议过什么」与
 *       「用户拒绝了什么」，被取消的高危提案本身就是风控价值。</li>
 * </ol>
 *
 * <h3>为什么入参是 {@code List<String>} 而不是框架的 {@code ToolExecution}</h3>
 * 编排层（T04）从 {@code Result.toolExecutions()} 里取出「本轮真实发生过的每次工具调用」，
 * 把每个工具的真实返回串丢进来即可。<b>不依赖 LangChain4j 类型</b>有两个好处：
 * 本层可以用纯 JUnit 单测覆盖（不需要起模型），且框架升级不会传染到业务代码。
 *
 * <h3>惰性过期而不是定时任务</h3>
 * 项目没有 {@code @EnableScheduling}。{@code PROPOSED → EXPIRED} 的行为等价于
 * 「下次有人用这个功能时顺手扫一遍」，对运营完全无感（过期卡片本来也不会被读），
 * 却省掉了一个新的后台线程与随之而来的关停/并发问题。
 */
@Service
public class AdminActionService {

    private static final Logger log = LoggerFactory.getLogger(AdminActionService.class);

    /** 对外展示时区（与 PRD 「+08:00」一致）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 提案 ID 前缀（{@code pa_} = proposal action）。 */
    private static final String PROPOSAL_ID_PREFIX = "pa_";

    /** 待确认操作常驻入口一次最多返回的提案数（防止历史积压把面板撑爆）。 */
    private static final int DEFAULT_PENDING_LIMIT = 20;

    private final ActionCatalog actionCatalog;
    private final AdminActionLogRepository actionLogRepository;

    /**
     * 只用于「工具返回串 → Map」与「Map → 落库 JSON」。
     * 刻意自己 new 一个而不是注入容器里的实例：这里不参与 HTTP 序列化，
     * 用一份独立配置可以避免将来有人给全局 ObjectMapper 加特性时意外影响审计内容。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminActionService(ActionCatalog actionCatalog,
                              AdminActionLogRepository actionLogRepository) {
        this.actionCatalog = actionCatalog;
        this.actionLogRepository = actionLogRepository;
    }

    // =====================================================================
    //  对外入口
    // =====================================================================

    /**
     * 批量编译本轮所有工具结果。
     * <p>
     * <b>单条失败绝不影响其它条</b>：工具说「ok=false」（预检不通过）→ 跳过；
     * 动作未注册/参数缺失/落库失败 → log.warn 后跳过。宁可少一张卡片，
     * 也不能因为一条脏数据让整个答复 500。
     *
     * @param toolResultJsonList 每个 {@code prepare_*} 工具的真实返回串（可能含非提案工具的结果）
     * @param chatMessageId      本轮用户消息 ID（可空），用于审计回放
     * @return 提案列表（按工具调用顺序）
     */
    public List<ActionProposal> buildProposals(List<String> toolResultJsonList, Long chatMessageId) {
        if (toolResultJsonList == null || toolResultJsonList.isEmpty()) {
            return List.of();
        }
        expireStaleProposals();

        List<ActionProposal> proposals = new ArrayList<>();
        for (String json : toolResultJsonList) {
            if (json == null || json.isBlank()) continue;
            try {
                ActionProposal proposal = compileProposal(json, chatMessageId);
                if (proposal != null) {
                    proposals.add(proposal);
                }
            } catch (Exception e) {
                // 未注册动作、JSON 结构异常、落库失败：一律丢弃该条，不影响其它提案
                log.warn("提案编译失败，已丢弃该条工具结果: {}", e.getMessage());
            }
        }
        return proposals;
    }

    /**
     * 把单个工具返回串编译成提案，并落库为 {@code PROPOSED}。
     *
     * @return 提案；工具返回 {@code ok=false}（预检不通过）时返回 {@code null} ——
     *         预检不通过<b>不生成卡片</b>，原因由对话文本承载
     * @throws IllegalArgumentException 动作未注册、targetId 非法、JSON 结构不符合约定
     * @throws BusinessException        身份上下文缺失（{@code CONTEXT_MISSING}）
     */
    public ActionProposal compileProposal(String toolResultJson, Long chatMessageId) {
        Map<String, Object> root = readObject(toolResultJson);
        if (!asBoolean(root.get("ok"), false)) {
            return null;   // 预检不通过：不出卡片
        }

        String actionRaw = asString(root.get("action"));
        AdminAction action = AdminAction.tryParse(actionRaw);
        if (action == null) {
            throw new IllegalArgumentException("未注册的动作: " + actionRaw);
        }

        Long targetId = asLong(root.get("targetId"));
        Map<String, Object> params = asMap(root.get("params"));
        Map<String, Object> snapshot = asMap(root.get("snapshot"));

        ActionProposal.Precheck precheck = toPrecheck(root.get("precheck"));
        if (!precheck.ok()) {
            // 工具自己说 ok=true 但 checks 里有不通过项：以 checks 为准，不出卡片
            throw new IllegalArgumentException("预检未全部通过，拒绝生成提案: " + action.name());
        }

        // 未注册动作在这里抛 IllegalArgumentException（renderConfirm 绝不返回 null）
        ActionProposal.Confirm confirm = actionCatalog.renderConfirm(action, targetId, params, snapshot);
        ActionProposal.Confirm reverse = actionCatalog.renderReverse(action, targetId, params, snapshot);

        String idempotencyKey = actionCatalog.renderIdempotencyKey(action, targetId, params, snapshot);
        boolean secondConfirm = actionCatalog.requiresSecondConfirm(action, targetId, params, snapshot);
        ActionProposal.Risk risk = toRisk(action, root.get("risk"), secondConfirm);

        LocalDateTime expiresAt = LocalDateTime.now().plus(ActionCatalog.PROPOSAL_TTL);
        String expiresAtText = expiresAt.atZone(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        ActionProposal proposal = new ActionProposal(
                newProposalId(),
                action.name(),
                action.label(),
                action.targetType().name(),
                targetId,
                asString(root.get("targetLabel")),
                params,
                precheck,
                risk,
                idempotencyKey,
                expiresAtText,
                snapshot,
                confirm,
                reverse,
                toFields(root.get("fields")));

        persistProposed(proposal, expiresAt, chatMessageId);
        return proposal;
    }

    /**
     * 惰性过期：把所有「已过有效期但仍 PROPOSED」的行翻成 EXPIRED。
     *
     * @return 本次过期的行数
     */
    public int expireStaleProposals() {
        LocalDateTime now = LocalDateTime.now();
        List<AdminActionLog> stale =
                actionLogRepository.findByStatusAndExpiresAtBefore(AdminActionLog.Status.PROPOSED, now);
        if (stale == null || stale.isEmpty()) {
            return 0;
        }
        for (AdminActionLog row : stale) {
            row.setStatus(AdminActionLog.Status.EXPIRED);
        }
        actionLogRepository.saveAll(stale);
        log.info("惰性过期提案 {} 条", stale.size());
        return stale.size();
    }

    /** 参数指纹（与 {@link ActionCatalog#paramsDigest(Map)} 同一实现，供执行器复核）。 */
    public String paramsDigest(Map<String, Object> params) {
        return ActionCatalog.paramsDigest(params);
    }

    // =====================================================================
    //  待确认操作常驻入口（刷新页面后仍能看到并执行卡片）
    // =====================================================================

    /**
     * 查询「当前操作人 · 未过期 · 待确认」的提案，供小运常驻入口渲染。
     *
     * <h3>安全边界</h3>
     * {@code operatorId} 由调用方从鉴权上下文取得（Controller 从
     * {@code AuthInterceptor.ATTR_USER_ID} 读），<b>本方法不接收任何前端可控的
     * operatorId</b> —— 否则改一个参数就能看到别人的待确认提案。
     *
     * <h3>惰性过期</h3>
     * 先复用 {@link #expireStaleProposals()} 把「已过期但仍 PROPOSED」的行翻成 EXPIRED，
     * 再做查询。因此过期卡片既不会出现在列表里，也不会在下次执行时才被拒绝。
     *
     * <h3>逐条容错</h3>
     * 坏 JSON、未注册动作等单条异常只 {@code log.warn} 后跳过，
     * <b>绝不因为一条脏数据让整批 500</b>（否则常驻入口会连累整个小运页面）。
     *
     * @param operatorId 当前登录用户 ID（唯一来源鉴权拦截器）
     * @param limit      最多返回条数（&lt;=0 时按 20 处理，上限也为 20）
     * @return 按 {@code created_at DESC} 排列的提案；无数据时返回空列表
     */
    public List<ActionProposal> pendingProposals(Long operatorId, int limit) {
        if (operatorId == null) {
            return List.of();
        }
        // 顺手把过期提案翻成 EXPIRED（复用既有逻辑，不另写一套）
        expireStaleProposals();

        int capped = (limit <= 0) ? DEFAULT_PENDING_LIMIT : Math.min(limit, DEFAULT_PENDING_LIMIT);
        List<AdminActionLog> rows =
                actionLogRepository.findByOperatorIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        operatorId,
                        AdminActionLog.Status.PROPOSED,
                        LocalDateTime.now(),
                        PageRequest.of(0, capped));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<ActionProposal> proposals = new ArrayList<>();
        for (AdminActionLog row : rows) {
            try {
                ActionProposal proposal = rebuildProposal(row);
                if (proposal != null) {
                    proposals.add(proposal);
                }
            } catch (Exception e) {
                // 单条脏数据不影响整批：未注册动作 / 反序列化失败 / 渲染失败都走这里
                log.warn("待确认提案重建失败，已跳过 id={} proposalId={} err={}",
                        row.getId(), row.getProposalId(), e.getMessage());
            }
        }
        return proposals;
    }

    /**
     * 从审计行重建一条提案（与当轮响应里的 {@code proposals[]} 结构完全一致）。
     *
     * <p><b>优先</b>反序列化 {@link AdminActionLog#getProposalJson()}：
     * 那是出卡片时的完整快照，能 100% 还原 fields / risk / targetLabel，前端渲染零差异。
     *
     * <p><b>回退</b>（老数据没有 proposal_json 列）：用已存的 params / precheck / snapshot，
     * 复用 {@link ActionCatalog} 的同一套渲染逻辑重建 path / body / 幂等键 / 风险判定。
     * 这里刻意<b>不</b>另写字段拼装 —— 展示字段在历史数据里本就没落库，缺失即不展示，
     * 绝不臆造。
     *
     * @return 可渲染的提案；动作已下线或结构不可用时返回 {@code null}
     */
    public ActionProposal rebuildProposal(AdminActionLog row) {
        if (row == null) {
            return null;
        }

        // ---------- 1) 首选：完整提案快照 ----------
        if (row.getProposalJson() != null && !row.getProposalJson().isBlank()) {
            try {
                ActionProposal stored = objectMapper.readValue(row.getProposalJson(), ActionProposal.class);
                if (isRenderable(stored)) {
                    return stored;
                }
                log.warn("提案快照不可渲染（动作未注册或缺少执行指令），尝试按列重建 id={}", row.getId());
            } catch (Exception e) {
                log.warn("提案快照反序列化失败，降级按列重建 id={} err={}", row.getId(), e.getMessage());
            }
        }

        // ---------- 2) 回退：用落库的 params/precheck/snapshot 重新渲染 ----------
        return rebuildFromColumns(row);
    }

    /** 提案的动作是否仍注册、正向执行指令是否可用（不可用的卡片点不动，不能展示）。 */
    private boolean isRenderable(ActionProposal proposal) {
        if (proposal == null || proposal.confirm() == null || proposal.confirm().path() == null) {
            return false;
        }
        AdminAction action = AdminAction.tryParse(proposal.action());
        return action != null && actionCatalog.isRegistered(action);
    }

    /**
     * 老数据回退：params/precheck/snapshot → 提案。
     * <p>字段拼装全部交给 {@link ActionCatalog} 与 {@link #toRisk}，与
     * {@link #compileProposal} 用的是同一套方法，不存在第二份口径。
     */
    private ActionProposal rebuildFromColumns(AdminActionLog row) {
        AdminAction action = AdminAction.tryParse(row.getAction());
        if (action == null || !actionCatalog.isRegistered(action)) {
            log.warn("提案动作未注册，跳过 id={} action={}", row.getId(), row.getAction());
            return null;
        }
        Long targetId = row.getTargetId();
        Map<String, Object> params = readMapOrEmpty(row.getParamsJson());
        Map<String, Object> snapshot = readMapOrEmpty(row.getSnapshotJson());
        ActionProposal.Precheck precheck = toPrecheck(readMapOrEmpty(row.getPrecheckJson()));

        ActionProposal.Confirm confirm = actionCatalog.renderConfirm(action, targetId, params, snapshot);
        ActionProposal.Confirm reverse = actionCatalog.renderReverse(action, targetId, params, snapshot);
        String idempotencyKey = actionCatalog.renderIdempotencyKey(action, targetId, params, snapshot);
        boolean secondConfirm = actionCatalog.requiresSecondConfirm(action, targetId, params, snapshot);
        ActionProposal.Risk risk = toRisk(action, null, secondConfirm);

        String expiresAtText = row.getExpiresAt() == null
                ? null
                : row.getExpiresAt().atZone(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new ActionProposal(
                row.getProposalId(),
                action.name(),
                action.label(),
                row.getTargetType() == null ? action.targetType().name() : row.getTargetType(),
                targetId,
                null,                    // targetLabel 未落库：宁可不展示，也不臆造
                params,
                precheck,
                risk,
                idempotencyKey,
                expiresAtText,
                snapshot,
                confirm,
                reverse,
                List.of());              // fields 未落库：回退路径不展示展示字段
    }

    // =====================================================================
    //  落库
    // =====================================================================

    private void persistProposed(ActionProposal proposal, LocalDateTime expiresAt, Long chatMessageId) {
        AdminActionContext ctx = AdminActionContext.current()
                .orElseThrow(() -> new BusinessException(500, "CONTEXT_MISSING", "身份上下文丢失，请重试一次"));

        AdminActionLog row = new AdminActionLog();
        row.setProposalId(proposal.proposalId());
        row.setAction(proposal.action());
        row.setTargetType(proposal.targetType());
        row.setTargetId(proposal.targetId());
        row.setParamsJson(writeJson(proposal.params()));
        row.setParamsDigest(ActionCatalog.paramsDigest(proposal.params()));
        row.setPrecheckJson(writeJson(proposal.precheck()));
        row.setSnapshotJson(writeJson(proposal.snapshot()));
        // 完整提案快照：刷新页面后「待确认操作」区块靠它原样重建卡片。
        // 与 params/precheck/snapshot 三列并存并不冗余 —— 那三列是执行校验口径，
        // 本列是展示口径（fields/risk/targetLabel 只在本列里）。
        row.setProposalJson(writeJson(proposal));
        // 【故意不写幂等键】`idempotency_key` 上有 UNIQUE（uk_aal_idem），而同一个动作完全可以被
        // 多次预检（用户说两遍"把退款单 8 通过"、卡片过期后重新预检）。若 PROPOSED 行就写键，
        // 第二次预检必然撞唯一键，卡片被静默丢弃 —— 那正是运营最莫名其妙的失败。
        // 因此：PROPOSED 行键为 NULL（UNIQUE 允许多行 NULL），键由执行器在
        // PROPOSED → CONFIRMED 的那一刻「抢占」，唯一约束在此刻才成为「一次唯一执行」的闸门。
        // 前端仍从 ActionProposal.idempotencyKey 拿到键并随确认请求带出；
        // 执行器可用 ActionCatalog.renderIdempotencyKey(action, targetId, params, snapshot) 复核防篡改。
        row.setIdempotencyKey(null);
        row.setStatus(AdminActionLog.Status.PROPOSED);
        row.setOperatorId(ctx.operatorId());
        row.setOperatorRole(ctx.operatorRole());
        row.setEntry(ctx.entry());
        row.setChatMessageId(chatMessageId != null ? chatMessageId : ctx.chatMessageId());
        row.setExpiresAt(expiresAt);
        row.setIp(ctx.ip());
        row.setUserAgent(ctx.userAgent());
        actionLogRepository.save(row);
    }

    // =====================================================================
    //  解析（工具返回串是「约定 JSON」，这里逐字段容错）
    // =====================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(String json) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具返回串不是合法 JSON 对象: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("工具返回串不是 JSON 对象");
        }
        return (Map<String, Object>) parsed;
    }

    /** 读取审计列里的 JSON 对象；空串/坏 JSON 一律按空 Map 处理（不抛错，避免整批失败）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(json, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception e) {
            log.warn("审计 JSON 解析失败，按空处理: {}", e.getMessage());
            return Map.of();
        }
    }

    private ActionProposal.Precheck toPrecheck(Object node) {
        Map<String, Object> map = asMap(node);
        if (map.isEmpty()) {
            return ActionProposal.Precheck.empty();
        }
        List<ActionProposal.Check> checks = new ArrayList<>();
        for (Object item : asList(map.get("checks"))) {
            Map<String, Object> c = asMap(item);
            checks.add(new ActionProposal.Check(
                    asString(c.get("code")),
                    asBoolean(c.get("ok"), false),
                    asString(c.get("message"))));
        }
        return new ActionProposal.Precheck(asBoolean(map.get("ok"), false), asString(map.get("checkedAt")), checks);
    }

    private List<ActionProposal.CardField> toFields(Object node) {
        List<ActionProposal.CardField> fields = new ArrayList<>();
        for (Object item : asList(node)) {
            Map<String, Object> f = asMap(item);
            String label = asString(f.get("label"));
            if (label == null || label.isBlank()) continue;
            String tone = asString(f.get("tone"));
            fields.add(new ActionProposal.CardField(
                    label,
                    asString(f.get("value")),
                    (tone == null || tone.isBlank()) ? "normal" : tone,
                    asBoolean(f.get("mono"), false)));
        }
        return fields;
    }

    /**
     * 合并两处风险信息：工具算出的「业务风险」（金额大/有同源活动/超 SLA）
     * 与服务端 {@code ActionCatalog} 的「阈值判定」（二次确认）。
     * <p>
     * <b>二次确认的最终裁决权在服务端</b>：即使工具忘了设，这里也会按阈值补上；
     * 反之工具设了而阈值没到，也保留工具的判断（宁多确认一次）。
     */
    private ActionProposal.Risk toRisk(AdminAction action, Object node, boolean secondConfirmByThreshold) {
        Map<String, Object> map = asMap(node);
        List<String> reasons = new ArrayList<>();
        for (Object r : asList(map.get("reasons"))) {
            String text = asString(r);
            if (text != null && !text.isBlank()) reasons.add(text);
        }
        boolean second = secondConfirmByThreshold || asBoolean(map.get("requiresSecondConfirm"), false);
        String level = asString(map.get("level"));
        if (level == null || level.isBlank()) {
            level = reasons.isEmpty() ? "LOW" : "MEDIUM";
        } else {
            level = level.trim().toUpperCase();
        }
        String hint = asString(map.get("secondConfirmHint"));
        if (hint == null || hint.isBlank()) {
            hint = actionCatalog.secondConfirmHint(action);
        }
        boolean irreversible = asBoolean(map.get("irreversible"), actionCatalog.isIrreversible(action));
        return new ActionProposal.Risk(level, reasons, second, second ? hint : null, irreversible,
                asString(map.get("note")));
    }

    // =====================================================================
    //  类型转换（工具返回的是 JSON，数值可能是 Integer/Long/String 混杂）
    // =====================================================================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static List<?> asList(Object v) {
        return v instanceof List<?> l ? l : List.of();
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean asBoolean(Object v, boolean fallback) {
        if (v == null) return fallback;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return fallback;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("审计字段序列化失败，降级为 toString: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    /** {@code pa_} + 10 位 hex，足够 1e12 量级不碰撞；同时作为审计表 {@code proposal_id}。 */
    private static String newProposalId() {
        return PROPOSAL_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
