package com.example.backend.ai.rag;

/**
 * 知识片段的<b>受众域</b>词汇表 —— RAG 分域检索的唯一口径。
 *
 * <h3>为什么需要「域」这个概念</h3>
 * 平台知识库原本是<b>一锅混装</b>的：既有「运费怎么算」「生鲜能退吗」这种顾客会问的规则，
 * 也有「GMV 怎么算」「后台角色有哪几种」这种只有运营才关心的内部口径。
 * 而 {@code AdminAssistant} / {@code CsAgent} / {@code ShopAssistant} 三个助手
 * <b>共用同一个检索器</b> —— 于是顾客问「今天有多少订单」时，
 * 检索会把「按天统计的时间范围怎么算」这条<b>后台口径</b>召回给他，
 * 助手拿着内部规则去回答顾客，答出来的话既不是顾客要的，也泄露了内部口径。
 * <p>
 * 实测过这件事的严重程度：不隔离时「怎么转人工？」（真问题）最高只有 0.769，
 * 而「今天天气怎么样」（无关问题）最高却有 0.777 —— <b>两个分数是交错的</b>，
 * 不存在任何一个阈值能同时「放过前者、挡住后者」。
 * 因为后者的高分正是被「按天统计」那条后台规则抬起来的。把域分开之后，
 * 无关问题的候选里不再有那条规则，分数带才重新拉开距离。
 *
 * <h3>标记写在知识文本里，而不是维护一张清单</h3>
 * {@link PlatformKnowledgeBase} 用 {@link #OPS_MARKER} 在 {@code ##} 标题行末尾声明本小节属于运营侧，
 * 解析时把标记剥掉（因此<b>不会进入正文，也不会影响向量</b>）。
 * 这样做而不是在代码里维护一个「哪些小节是运营的」标题清单，是因为：
 * <ul>
 *   <li><b>改标题不会漏改</b>：清单靠标题字符串匹配，标题一改就静默失配、悄悄从 ops 掉回 customer；
 *       标记写在标题行本身，改标题时它就在眼前；</li>
 *   <li><b>读知识文本时一眼能看出受众</b>，新增小节时不会因为不知道有这张清单而忘记登记。</li>
 * </ul>
 * 缺省（不写标记）一律视为 {@link #CUSTOMER} —— 这个方向的兜底是安全的：
 * 「顾客多看到一条本该只给运营看的规则」是可接受的降级，
 * 而反过来「顾客该问的规则被藏起来」会直接让助手答不出正常问题。
 */
public final class RagScope {

    /** 片段元数据里存放受众域的键。 */
    public static final String KEY = "scope";

    /** 顾客侧：{@code ShopAssistant}（小发）与 {@code CsAgent}（AI 客服）看得见。 */
    public static final String CUSTOMER = "customer";

    /** 运营侧：只在后台（{@code AdminAssistant} 小运）可见，不注入顾客侧对话。 */
    public static final String OPS = "ops";

    /** 不做任何过滤（管理页「全量」检索、小运使用）。 */
    public static final String ALL = "all";

    /**
     * 知识文本里声明「本小节属于运营侧」的标记，写在 {@code ## } 标题行末尾。
     * <p>例如 {@code ## 后台角色与权限  <!-- ops -->}。解析时会被剥掉。
     */
    public static final String OPS_MARKER = "<!-- ops -->";

    private RagScope() {
    }

    /** 是否为已知的域取值。 */
    public static boolean isValid(String scope) {
        return CUSTOMER.equals(scope) || OPS.equals(scope) || ALL.equals(scope);
    }

    /** 空值兜底为 {@link #ALL}（管理页不传 scope 时按全量检索）。 */
    public static String normalize(String scope) {
        if (scope == null || scope.isBlank()) {
            return ALL;
        }
        String trimmed = scope.trim().toLowerCase();
        return isValid(trimmed) ? trimmed : ALL;
    }
}
