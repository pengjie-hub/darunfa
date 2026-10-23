package com.example.backend.ai.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 请求级的「操作者身份上下文」。
 *
 * <h3>它解决什么问题</h3>
 * 「小运」的只读工具（{@code AdminDataTools}）不需要知道「谁在问」；但一旦要预检
 * <b>退款审批</b>（禁止自审）、<b>发货</b>（需要 {@code order:ship}），工具就必须拿到
 * 操作人身份。而 LangChain4j 的工具参数是<b>模型可见</b>的 Schema ——
 * 把 {@code operatorId} 写成工具参数，等于把「伪造身份」的入口直接交给模型，
 * 一次提示词注入就能让它填别人的 ID。
 *
 * <p>所以身份走这条<b>不经过模型</b>的通路：Controller 在请求线程上
 * {@code bind(...)}，工具在同线程 {@code current()} 读取，请求结束
 * {@code try-with-resources} 自动 {@code unbind()}。模型看到的工具签名里
 * 根本没有 operatorId 这个字段。
 *
 * <h3>三个安全要点（对应技术设计 3.5）</h3>
 * <ol>
 *   <li><b>不进模型 Schema</b>：本类不是工具参数，模型看不到也伪造不了。</li>
 *   <li><b>双保险交叉校验</b>：每个 {@code prepare_*} 都还有 {@code @ToolMemoryId}，
 *       工具内用 {@code JpaChatMemoryStore.resolveUserId(memoryKey)} 与
 *       {@link #operatorId()} 比对，不一致即 {@code OPERATOR_MISMATCH}。</li>
 *   <li><b>缺失即拒绝</b>：{@link #current()} 为空时（例如工具被调度到别的线程），
 *       工具必须返回 {@code CONTEXT_MISSING} 并停止，<b>绝不降级为「跳过权限校验继续干」</b>。</li>
 * </ol>
 *
 * <h3>为什么只承担「身份」，不承担「数据」</h3>
 * 提案数据的采集走框架的 {@code Result.toolExecutions()}（本轮真实发生过的工具调用），
 * 而不是往这里塞一个 {@code List} 累积。ThreadLocal 累积有两个坑：线程复用未清理会把
 * 上一轮的卡片漏到下一轮；工具若在别的线程执行则会直接丢。职责单一，问题就不存在。
 *
 * <h3>角色为什么不参与鉴权</h3>
 * {@link #operatorRole()} 只用于写审计列 {@code operator_role}。权限判断一律走
 * {@link #hasAnyPerm(String...)}（本质是 {@code perms.contains(any)}），
 * 不写 {@code if (role == ADMIN) return true} —— ADMIN 之所以能操作，是因为它在
 * {@code DataInitializer} 里被授予了全部权限码；若将来从 ADMIN 上收回某个码，
 * 工具与接口必须同时拒绝，而不是「接口拒了、卡片看起来还能点」。
 */
public final class AdminActionContext implements AutoCloseable {

    /** 对话入口（小运对话框）。 */
    public static final String ENTRY_CHAT = "CHAT";

    /** 后台页面入口（既有写按钮，无幂等键）。 */
    public static final String ENTRY_PAGE = "PAGE";

    /** 直接调接口（脚本/联调）。 */
    public static final String ENTRY_API = "API";

    private static final ThreadLocal<AdminActionContext> HOLDER = new ThreadLocal<>();

    private final Long operatorId;
    private final String operatorRole;
    private final Set<String> perms;
    private final String entry;
    private final Long chatMessageId;
    private final String ip;
    private final String userAgent;

    private AdminActionContext(Long operatorId,
                               String operatorRole,
                               Set<String> perms,
                               String entry,
                               Long chatMessageId,
                               String ip,
                               String userAgent) {
        this.operatorId = operatorId;
        this.operatorRole = operatorRole;
        this.perms = perms == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(perms));
        this.entry = normalizeEntry(entry);
        this.chatMessageId = chatMessageId;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    /**
     * 绑定当前线程的身份上下文。
     * <p>
     * 只应在 {@code AiAssistantController.ask} 这类「一次请求一次绑定」的位置调用，
     * 且必须配合 try-with-resources 或 finally 调用 {@link #unbind()}。
     *
     * @param operatorId 唯一来源 {@code AuthInterceptor.ATTR_USER_ID}
     * @param role       唯一来源 {@code AuthInterceptor.ATTR_ROLE}（实时查库的角色）
     * @param perms      唯一来源 {@code AuthInterceptor.ATTR_PERMS}
     * @param entry      {@link #ENTRY_CHAT} / {@link #ENTRY_PAGE} / {@link #ENTRY_API}
     */
    public static AdminActionContext bind(Long operatorId,
                                          String role,
                                          Set<String> perms,
                                          String entry,
                                          Long chatMessageId,
                                          String ip,
                                          String userAgent) {
        AdminActionContext ctx = new AdminActionContext(operatorId, role, perms, entry, chatMessageId, ip, userAgent);
        HOLDER.set(ctx);
        return ctx;
    }

    /** 读取当前线程的身份上下文；未绑定或已解绑时返回空。 */
    public static Optional<AdminActionContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /** 解绑当前线程的身份上下文（必须放在 finally 或 close 中）。 */
    public static void unbind() {
        HOLDER.remove();
    }

    /** 支持 try-with-resources，保证异常路径也能解绑。 */
    @Override
    public void close() {
        unbind();
    }

    /**
     * 是否持有任一权限码（OR 语义，与 {@code AuthInterceptor} 的判定完全一致）。
     *
     * @param codes 权限码，如 {@code "order:view", "cs:manage"}
     * @return 命中任一即为 true；{@code codes} 为空时返回 false（宁拒绝不放行）
     */
    public boolean hasAnyPerm(String... codes) {
        if (codes == null || codes.length == 0) return false;
        for (String code : codes) {
            if (code != null && this.perms.contains(code)) return true;
        }
        return false;
    }

    /** 是否持有全部权限码（AND 语义；当前没有动作使用，保留以便后续组合校验）。 */
    public boolean hasAllPerms(String... codes) {
        if (codes == null || codes.length == 0) return false;
        for (String code : codes) {
            if (code == null || !this.perms.contains(code)) return false;
        }
        return true;
    }

    public Long operatorId() {
        return operatorId;
    }

    /** 仅供写审计列，<b>不得</b>用于权限判断。 */
    public String operatorRole() {
        return operatorRole;
    }

    public Set<String> perms() {
        return perms;
    }

    public String entry() {
        return entry;
    }

    public Long chatMessageId() {
        return chatMessageId;
    }

    public String ip() {
        return ip;
    }

    public String userAgent() {
        return userAgent;
    }

    private static String normalizeEntry(String entry) {
        if (entry == null || entry.isBlank()) return ENTRY_API;
        String e = entry.trim().toUpperCase();
        return switch (e) {
            case ENTRY_CHAT -> ENTRY_CHAT;
            case ENTRY_PAGE -> ENTRY_PAGE;
            default -> ENTRY_API;
        };
    }

    @Override
    public String toString() {
        return "AdminActionContext{operatorId=" + operatorId
                + ", role=" + operatorRole
                + ", entry=" + entry
                + ", perms=" + perms.size() + " 项}";
    }
}
