package com.example.backend.service;

import com.example.backend.ai.action.ActionCatalog;
import com.example.backend.ai.action.AdminAction;
import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.common.BusinessException;
import com.example.backend.common.DuplicateActionException;
import com.example.backend.entity.AdminActionLog;
import com.example.backend.repository.AdminActionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 写操作执行器：<b>幂等 + 审计</b>的唯一落地入口。
 *
 * <h2>它包在哪一层</h2>
 * 包在既有 REST 方法的<b>最外层</b>：{@code 控制器方法} → {@link #run} → 业务 Service。
 * 因此对话入口与后台页面入口走的是<b>同一条执行路径</b>（同一段业务代码、同一套状态机），
 * 不会出现「对话能退、页面不能退」的两套逻辑。
 *
 * <pre>
 *   AdminController.approveRefund(...)
 *     └─ actionExecutor.run(cmd, RefundDTO.class, () -&gt; refundService.approve(operatorId, id))
 *          ├─ 1. 定位提案行（对话入口按 X-Proposal-Id；页面入口无提案）
 *          ├─ 2. 校验：动作/对象/操作人/有效期/参数一致性   ← 防跨提案误用、防篡改
 *          ├─ 3. 抢占幂等键（此刻才写 idempotency_key，撞 UNIQUE = 已执行过）
 *          ├─ 4. 执行业务 Supplier
 *          └─ 5. 置 EXECUTED / FAILED + 结果快照
 * </pre>
 *
 * <h2>两条不可动摇的纪律</h2>
 * <ol>
 *   <li><b>审计绝不拖累业务</b>（架构师 R1）：本类的所有落库动作都包在 try/catch 里，
 *       失败只 {@code log.warn}。绝不允许「日志表写不进去导致退款失败」。
 *       唯一会向上抛的是<b>业务语义</b>的拒绝（提案已过期、参数被改、重复执行）。</li>
 *   <li><b>幂等键不信任前端</b>：对话入口的键由服务端用
 *       {@link ActionCatalog#renderIdempotencyKey} 按<b>落库行</b>的 params/snapshot 重算；
 *       前端传来的值只用来比对（不一致即 {@code PROPOSAL_MISMATCH}），不直接采用。</li>
 * </ol>
 *
 * <h2>为什么 class 上没有 {@code @Transactional}</h2>
 * 业务方法各自带事务（{@code RefundService.approve} 等）。执行器若也开事务，
 * 业务异常会把「FAILED 审计行」一起回滚 —— 那恰好丢掉了最有价值的那条记录
 *（「谁试图做什么、为什么失败」）。因此执行器的每次落库都是独立事务。
 */
@Service
public class AdminActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AdminActionExecutor.class);

    /** 页面入口（无提案、无幂等键）。 */
    private static final String ENTRY_PAGE = AdminActionContext.ENTRY_PAGE;

    /** 对话入口（带 X-Proposal-Id）。 */
    private static final String ENTRY_CHAT = AdminActionContext.ENTRY_CHAT;

    /** 直接调接口（带 Idempotency-Key 但没有提案）。 */
    private static final String ENTRY_API = AdminActionContext.ENTRY_API;

    private final AdminActionLogRepository actionLogRepository;
    private final ActionCatalog actionCatalog;

    /** 只用于读写审计里的 JSON 列，与 HTTP 序列化无关。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminActionExecutor(AdminActionLogRepository actionLogRepository, ActionCatalog actionCatalog) {
        this.actionLogRepository = actionLogRepository;
        this.actionCatalog = actionCatalog;
    }

    // =====================================================================
    //  对外入口
    // =====================================================================

    /**
     * 执行一次写操作，并承担幂等与审计。
     *
     * @param cmd        执行指令（动作、对象、参数、提案 ID、幂等键、操作人）
     * @param resultType 业务返回值类型，用于「重复执行时回放上次结果」（可空）
     * @param business   真正的业务调用（既有 Service 方法），<b>原样搬进来</b>
     * @return 业务返回值
     */
    public <T> T run(Command cmd, Class<T> resultType, Supplier<T> business) {
        Reservation reservation = reserve(cmd);
        if (reservation.mode() == Mode.ALREADY_DONE) {
            return replay(reservation.row(), resultType);
        }
        if (reservation.mode() == Mode.IN_PROGRESS) {
            throw new BusinessException(409, "ACTION_IN_PROGRESS",
                    "这个操作正在执行中（另一个请求刚开始），请稍等几秒再刷新，不要重复点击。");
        }

        try {
            T result = business.get();
            complete(reservation.row(), result, null);
            return result;
        } catch (RuntimeException e) {
            complete(reservation.row(), null, e);
            throw e;
        } catch (Exception e) {
            // Supplier 理论上只抛 RuntimeException；兜底保证审计仍能落一条 FAILED
            complete(reservation.row(), null, e);
            throw new BusinessException(500, "INTERNAL_ERROR", "执行失败: " + safeMessage(e));
        }
    }

    // =====================================================================
    //  1~3：定位提案行 → 校验 → 抢占幂等键
    // =====================================================================

    private Reservation reserve(Command cmd) {
        AdminActionLog row = null;

        // ---------- 定位 ----------
        if (cmd.proposalId() != null && !cmd.proposalId().isBlank()) {
            try {
                row = actionLogRepository.findFirstByProposalId(cmd.proposalId())
                        .orElseThrow(() -> new BusinessException(400, "PROPOSAL_MISMATCH",
                                "提案不存在或已失效，请让小运重新准备一次。"));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                // 读审计失败：不让它挡住业务，降级为「无提案」继续（只是失去幂等与提案校验）
                log.warn("读取提案失败，降级为无提案执行 proposalId={} err={}", cmd.proposalId(), e.getMessage());
            }
        }

        if (row != null) {
            // 校验失败必须抛给用户（这是业务语义，不是运维故障）
            verifyProposal(row, cmd);

            if (row.getStatus() == AdminActionLog.Status.EXECUTED) {
                return new Reservation(row, Mode.ALREADY_DONE);
            }
            if (row.getStatus() == AdminActionLog.Status.CONFIRMED) {
                return new Reservation(row, Mode.IN_PROGRESS);
            }
        } else {
            // 页面入口 / 直接调接口：没有提案，现造一行用于审计
            row = newRow(cmd);
        }

        // ---------- 抢占幂等键 ----------
        String serverKey = resolveServerKey(row, cmd);
        if (serverKey != null) {
            Optional<AdminActionLog> occupant = findByKey(serverKey);
            if (occupant.isPresent()) {
                AdminActionLog other = occupant.get();
                if (row.getId() != null && row.getId().equals(other.getId())) {
                    // 同一个提案行的重试：继续往下走（FAILED 重试）
                } else if (other.getStatus() == AdminActionLog.Status.EXECUTED) {
                    return new Reservation(other, Mode.ALREADY_DONE);
                } else if (other.getStatus() == AdminActionLog.Status.CONFIRMED) {
                    return new Reservation(other, Mode.IN_PROGRESS);
                }
            }
            row.setIdempotencyKey(serverKey);
        }
        row.setStatus(AdminActionLog.Status.CONFIRMED);
        row.setConfirmedAt(LocalDateTime.now());
        if (row.getAttempt() == null || row.getAttempt() < 1) {
            row.setAttempt(1);
        }

        try {
            // saveAndFlush：让 UNIQUE(uk_aal_idem) 在这里立刻生效，而不是等到事务末尾
            row = actionLogRepository.saveAndFlush(row);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // 两个请求同时确认同一张卡片：后到的那个撞唯一键 → 按「已执行」处理
            log.warn("幂等键冲突，判定为重复执行 key={}", serverKey);
            throw new BusinessException(0, "DUPLICATE_ACTION",
                    "这个操作刚刚已经执行过了，请勿重复点击（刷新即可看到最新状态）。");
        } catch (Exception e) {
            // 审计写不进去：绝不阻塞业务（R1）。只是这一次失去幂等保护，风险由业务状态机兜底
            log.warn("审计写入失败，继续执行业务 action={} targetId={} err={}",
                    cmd.action(), cmd.targetId(), e.getMessage());
        }
        return new Reservation(row, Mode.EXECUTE);
    }

    /**
     * 提案行校验：动作/对象必须对得上，操作人必须是本人，必须在有效期内，参数不得被改。
     *
     * <p>这五项缺一不可：少了「动作对得上」，一张退款提案就能被改成发货请求；
     * 少了「操作人对得上」，A 的提案能被 B 拿来执行。
     */
    private void verifyProposal(AdminActionLog row, Command cmd) {
        if (!cmd.action().name().equals(row.getAction())) {
            throw new BusinessException(400, "PROPOSAL_MISMATCH",
                    "提案与实际操作不一致（提案是 " + row.getAction() + "），请重新让小运准备。");
        }
        if (row.getTargetId() != null && cmd.targetId() != null && !row.getTargetId().equals(cmd.targetId())) {
            throw new BusinessException(400, "PROPOSAL_MISMATCH",
                    "提案针对的对象是 " + row.getTargetId() + "，与实际操作的 " + cmd.targetId() + " 不一致。");
        }
        if (row.getOperatorId() != null && cmd.operatorId() != null && !row.getOperatorId().equals(cmd.operatorId())) {
            throw new BusinessException(400, "PROPOSAL_MISMATCH", "这个提案不是为你准备的，请重新让小运准备。");
        }
        if (row.getStatus() == AdminActionLog.Status.EXPIRED) {
            throw new BusinessException(410, "PROPOSAL_EXPIRED", "提案已过期（数据可能已变化），请重新让小运准备。");
        }
        if (row.getStatus() == AdminActionLog.Status.CANCELLED) {
            throw new BusinessException(410, "PROPOSAL_EXPIRED", "提案已取消，请重新让小运准备。");
        }
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())
                && row.getStatus() == AdminActionLog.Status.PROPOSED) {
            markExpired(row);
            throw new BusinessException(410, "PROPOSAL_EXPIRED", "提案已过期（有效期 5 分钟），请重新让小运准备。");
        }
        verifyParams(row, cmd);
    }

    /**
     * 参数一致性：只比对「请求里带了、提案里也有」的同名键。
     * <p>
     * 为什么不做整串 digest 比对：接口能收到的参数只是提案参数的一个子集
     *（例如发货接口的 body 只有 company/trackingNo，提案里还有 orderNo），
     * 整串比对会 100% 误报。逐键比对既能抓住「改了运单号」这种篡改（验收 ⑫），
     * 又不会因为「提案里多一个前端拿不到的键」而误拦。
     */
    private void verifyParams(AdminActionLog row, Command cmd) {
        if (cmd.params() == null || cmd.params().isEmpty()) return;
        Map<String, Object> stored = readMap(row.getParamsJson());
        if (stored.isEmpty()) return;
        for (Map.Entry<String, Object> e : cmd.params().entrySet()) {
            if (!stored.containsKey(e.getKey())) continue;
            if (!sameValue(stored.get(e.getKey()), e.getValue())) {
                throw new BusinessException(400, "PROPOSAL_MISMATCH",
                        "请求参数与确认卡片不一致（" + e.getKey() + "），请刷新后重试或重新让小运准备。");
            }
        }
    }

    /**
     * 服务端计算幂等键（不信任前端）。
     * <p>
     * 对话入口用<b>落库行</b>的 params/snapshot 重算，因此运营改不了键；
     * 前端若同时传了键，必须与服务端一致，否则判定篡改。
     */
    private String resolveServerKey(AdminActionLog row, Command cmd) {
        String serverKey = cmd.idempotencyKey();
        try {
            Map<String, Object> params = readMap(row.getParamsJson());
            Map<String, Object> snapshot = readMap(row.getSnapshotJson());
            AdminAction action = AdminAction.tryParse(row.getAction());
            if (action != null) {
                String recomputed = actionCatalog.renderIdempotencyKey(action, row.getTargetId(), params, snapshot);
                if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()
                        && !cmd.idempotencyKey().equals(recomputed)) {
                    throw new BusinessException(400, "PROPOSAL_MISMATCH",
                            "幂等键与提案不一致（可能被改动过），请刷新后重试。");
                }
                serverKey = recomputed;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 渲染失败（例如未注册动作）：退回前端传来的键，至少不失去幂等
            log.warn("服务端重算幂等键失败，退回请求头中的键 err={}", e.getMessage());
        }
        // 页面入口（无提案、无键）：不做幂等，只留审计
        if (row.getId() == null && cmd.proposalId() == null
                && (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank())) {
            return null;
        }
        return serverKey;
    }

    // =====================================================================
    //  4~5：结果落库
    // =====================================================================

    private void complete(AdminActionLog row, Object result, Exception error) {
        if (row == null) return;
        try {
            if (error == null) {
                row.setStatus(AdminActionLog.Status.EXECUTED);
                row.setExecutedAt(LocalDateTime.now());
                row.setResultJson(writeJson(result));
                row.setErrorCode(null);
                row.setErrorMessage(null);
            } else {
                row.setStatus(AdminActionLog.Status.FAILED);
                row.setExecutedAt(LocalDateTime.now());
                row.setAttempt(row.getAttempt() == null ? 1 : row.getAttempt() + 1);
                if (error instanceof BusinessException be) {
                    row.setErrorCode(be.getSubCode() == null ? "BUSINESS_ERROR" : be.getSubCode());
                } else {
                    row.setErrorCode("INTERNAL_ERROR");
                }
                row.setErrorMessage(truncate(safeMessage(error), 500));
            }
            actionLogRepository.save(row);
        } catch (Exception e) {
            // R1：审计失败绝不改变业务结果
            log.warn("审计结果写入失败（已忽略） proposalId={} err={}", row.getProposalId(), e.getMessage());
        }
    }

    private void markExpired(AdminActionLog row) {
        try {
            row.setStatus(AdminActionLog.Status.EXPIRED);
            actionLogRepository.save(row);
        } catch (Exception e) {
            log.warn("标记过期失败（已忽略） id={} err={}", row.getId(), e.getMessage());
        }
    }

    /**
     * 重复执行：回放上次结果。
     *
     * <p>一律抛 {@link DuplicateActionException}（{@code code=0, subCode=DUPLICATE_ACTION}），
     * 由全局处理器把「上次的结果」塞进响应体 {@code data}。
     * <b>不在这里 return 缓存对象</b> —— 那样控制器会走到 {@code ApiResponse.ok(dto)}，
     * 前端只能看到一个「和成功一模一样」的响应，分不清是这次刚执行还是上次的旧结果，
     * 也就无法把卡片置成「已执行过」态（验收 ⑥）。
     *
     * <p>回放失败（上次结果没落库 / 反序列化失败 / 本动作无返回值）时 payload 为 null，
     * 前端仍能凭 {@code subCode} 判定「已执行过」，只是没有结果摘要可展示。
     */
    private <T> T replay(AdminActionLog row, Class<T> resultType) {
        Object replayed = null;
        String json = row == null ? null : row.getResultJson();
        if (json != null && !json.isBlank() && resultType != null && resultType != Void.class) {
            try {
                replayed = objectMapper.readValue(json, resultType);
            } catch (Exception e) {
                log.warn("回放上次结果失败，仅返回「已执行过」提示: {}", e.getMessage());
            }
        }
        if (replayed != null) {
            log.info("重复执行命中幂等，回放上次结果 key={}", row.getIdempotencyKey());
        }
        throw new DuplicateActionException("该操作已执行过，下面是上次的结果。", replayed);
    }

    // =====================================================================
    //  内部工具
    // =====================================================================

    private AdminActionLog newRow(Command cmd) {
        AdminActionLog row = new AdminActionLog();
        row.setAction(cmd.action().name());
        row.setTargetType(cmd.action().targetType().name());
        row.setTargetId(cmd.targetId());
        row.setParamsJson(writeJson(cmd.params()));
        row.setParamsDigest(ActionCatalog.paramsDigest(cmd.params()));
        row.setOperatorId(cmd.operatorId());
        // 角色优先用调用方显式传入的快照（页面入口）；对话入口没传则取线程上下文里绑定的角色
        row.setOperatorRole(cmd.operatorRole() != null ? cmd.operatorRole() : currentRole());
        row.setEntry(resolveEntry(cmd));
        row.setChatMessageId(null);
        row.setIp(currentIp());
        row.setUserAgent(currentUserAgent());
        row.setStatus(AdminActionLog.Status.PROPOSED);
        return row;
    }

    /**
     * 入口判定：<b>由请求头与提案 ID 推导，不接受前端自报</b>。
     * 有提案 → CHAT；有幂等键但无提案 → API；都没有 → PAGE。
     */
    private static String resolveEntry(Command cmd) {
        if (cmd.proposalId() != null && !cmd.proposalId().isBlank()) return ENTRY_CHAT;
        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) return ENTRY_API;
        return ENTRY_PAGE;
    }

    private Optional<AdminActionLog> findByKey(String key) {
        try {
            return actionLogRepository.findFirstByIdempotencyKey(key);
        } catch (Exception e) {
            log.warn("按幂等键查询失败（视为不存在） key={} err={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("审计 JSON 序列化失败，跳过该列: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object parsed = objectMapper.readValue(json, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception e) {
            log.warn("审计 JSON 解析失败，按空处理: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 宽松相等：数值按数值比（{@code 200} == {@code 200.00}），时间按 ISO 解析后比
     *（{@code 2026-11-11T00:00} == {@code 2026-11-11T00:00:00}），其余按字符串。
     * <p>
     * 之所以要宽松：提案里的值是工具写的（字符串），接口收到的是 Jackson 反序列化的
     * （BigDecimal / LocalDateTime），严格 toString 比对会 100% 误报。
     */
    private static boolean sameValue(Object expected, Object actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        BigDecimal e1 = toDecimal(expected);
        BigDecimal a1 = toDecimal(actual);
        if (e1 != null && a1 != null) return e1.compareTo(a1) == 0;

        LocalDateTime t1 = toTime(expected);
        LocalDateTime t2 = toTime(actual);
        if (t1 != null && t2 != null) return t1.equals(t2);

        return String.valueOf(expected).trim().equals(String.valueOf(actual).trim());
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toTime(Object v) {
        if (v instanceof LocalDateTime t) return t;
        String s = String.valueOf(v).trim().replace(' ', 'T');
        if (s.length() < 10) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String currentRole() {
        return AdminActionContext.current().map(AdminActionContext::operatorRole).orElse(null);
    }

    private static String currentIp() {
        return AdminActionContext.current().map(AdminActionContext::ip).orElse(null);
    }

    private static String currentUserAgent() {
        return AdminActionContext.current().map(AdminActionContext::userAgent).orElse(null);
    }

    private static String safeMessage(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // =====================================================================
    //  内部类型
    // =====================================================================

    /** 执行模式。 */
    private enum Mode {
        /** 正常执行。 */
        EXECUTE,
        /** 已经执行过 → 回放结果。 */
        ALREADY_DONE,
        /** 另一个请求正在执行 → 409。 */
        IN_PROGRESS
    }

    /** 抢占结果：要执行的那一行 + 模式。 */
    private record Reservation(AdminActionLog row, Mode mode) {
    }

    /**
     * 执行指令。控制器只需把「动作 / 对象 / 参数 / 提案 ID / 幂等键 / 操作人」传进来，
     * 其余（入口判定、审计字段、幂等）都由执行器负责。
     *
     * @param action         动作
     * @param targetId       对象主键（新建类动作可为 null）
     * @param params         与提案一致的执行参数（用于一致性校验）
     * @param proposalId     提案 ID（请求头 {@code X-Proposal-Id}）；页面入口为 null
     * @param idempotencyKey 幂等键（请求头 {@code Idempotency-Key}）；页面入口为 null
     * @param operatorId     操作人（唯一来源 {@code AuthInterceptor}）
     * @param operatorRole   操作人角色快照（审计用）；为 null 时由执行器自行从请求属性解析
     */
    public record Command(AdminAction action,
                          Long targetId,
                          Map<String, Object> params,
                          String proposalId,
                          String idempotencyKey,
                          Long operatorId,
                          String operatorRole) {

        /** 六参便捷构造：角色留空，由执行器解析（页面入口的常规用法）。 */
        public static Command of(AdminAction action, Long targetId, Map<String, Object> params,
                                 String proposalId, String idempotencyKey, Long operatorId) {
            return of(action, targetId, params, proposalId, idempotencyKey, operatorId, null);
        }

        /** 七参构造：显式给出角色快照（目前对话入口/调用方愿意显式传时使用）。 */
        public static Command of(AdminAction action, Long targetId, Map<String, Object> params,
                                 String proposalId, String idempotencyKey, Long operatorId,
                                 String operatorRole) {
            return new Command(action, targetId, params == null ? Map.of() : params,
                    proposalId, idempotencyKey, operatorId, operatorRole);
        }

        /** 参数构建小工具（避免控制器里写一堆 Map.of / LinkedHashMap）。 */
        public static Map<String, Object> params(Object... kv) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                map.put(String.valueOf(kv[i]), kv[i + 1]);
            }
            return map;
        }
    }
}
