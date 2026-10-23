package com.example.backend.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量检索（RAG）层参数（绑定 {@code ai.rag.*}）。
 *
 * <h3>这几个参数分别在调什么</h3>
 * <ul>
 *   <li>{@code enabled} —— 总开关。关掉后助手退化成「会话记忆 + 工具调用」，行为与加 RAG 之前完全一致。
 *       它只影响<b>是否播种知识库</b>，不影响 Bean 的装配（见 {@link RagConfig} 的说明）。</li>
 *   <li>{@code top-k} —— 每次问答最终注入几个知识片段。调大 = 上下文更全但更贵、
 *       也更容易引入无关噪声；平台知识本身不长，3 条足以覆盖「退款规则」「发货时效」这类单点问题。</li>
 *   <li>{@code min-score} —— 相似度下限。<b>这是 RAG 里最容易被忽视却最关键的一个参数</b>：
 *       没有下限时，用户问「今天有多少订单」也会硬塞进 3 条「退换货政策」，反而污染模型判断；
 *       设了下限后，知识库里没有的内容就干脆不注入，让模型老老实实去调工具或承认不知道。</li>
 * </ul>
 *
 * <h3>⚠️ 为什么会有两个 min-score（以及为什么不能只用一个）</h3>
 * 2026-10-09 用 BGE-small-zh-v1.5 在 20 个问句 × 3 个受众域上做了实测
 * （走真实的 {@code /api/admin/ai/knowledge/search}，不是另写一套近似实现），
 * 得到一个<b>推翻旧结论</b>的结果：
 * <pre>
 *   [分域前 · 全量池]  正例最高分带 0.769 ~ 0.900，其中最低的是「怎么转人工？」
 *                     负例最高分带 0.636 ~ 0.777，其中最高的是「今天天气怎么样」
 * </pre>
 * <b>正例最低 0.769 &lt; 负例最高 0.777</b> —— 两个分布是<b>交错</b>的，
 * 因此<b>不存在任何一个单一阈值</b>能同时做到「放过全部真问题」与「挡住全部无关问题」。
 * 早先文档里写的「0.78 正好落在两组之间的空隙里」在当时那批问题上碰巧成立，
 * 换一批问题就失效了：0.78 既放过了跨域误召（「今天有多少订单？」= 0.802），
 * 又拦掉了真问题（「生鲜能退吗？」= 0.7796、「怎么转人工？」= 0.769）。
 * <p>
 * 根因不是阈值选得不好，而是<b>候选池本身是混的</b>：23 个小节里混着 6 条后台运营规则，
 * 三个助手却共用同一个检索器。把域分开（{@link RagScope}）之后：
 * <pre>
 *   [分域后 · 顾客池]  正例 0.780 ~ 0.935   负例 0.632 ~ 0.724   空隙 +0.056（0.75 落在空隙里）
 *   [分域后 · 全量池]  业务口径类问句 ≥ 0.802；闲聊最高 0.777       空隙 +0.025（0.79 落在空隙里）
 * </pre>
 * 顾客池之所以突然能分开了，是因为「今天天气怎么样」原来那 0.777 正是被
 * 「按天统计的时间范围怎么算」这条<b>后台</b>小节抬起来的 —— 它一旦离开顾客池，
 * 这条问句在顾客池只剩 0.689。
 * <p>
 * 两个值因此<b>必须各自校准</b>，不能互相照搬：<b>候选池的成分不同，分数分布就不同。</b>
 * 换嵌入模型或大幅增删知识文本后都要重新测 —— 同一模型下知识文本一变，分数带也会整体平移。
 *
 * <h3>词面加权（P3）为什么默认「只按标题加权」</h3>
 * 见 {@link LexicalTitleBooster}：向量分之外加上「问句的实词与小节标题的字面重合度」。
 * 用来救被短问句压低分、但字面几乎逐字命中标题的真问题 ——
 * 实测「怎么转人工？」因此从 0.769 升到 0.819，而<b>全部负例一分未动</b>。
 * <p>
 * 正文也加权则是<b>有反例的</b>：给正文加权后，「今天天气怎么样」因为命中
 * 「按天统计…」正文里的「今天」而拿到 +0.007（0.777 → 0.784），
 * 恰好会越过全量池的门槛。同一个机制既奖励了「生鲜」命中退货规则正文，
 * 也奖励了「今天」命中统计口径正文 —— <b>加性权重无法区分「正文确实在讲这件事」
 * 与「正文只是顺带提了一句」</b>。因此 {@code hybrid-body-boost} 默认为 0，
 * 保留为可调参数，需要时再实测开启。
 * <p>
 * 加项权重都取在百分位级别：它的职责是<b>把本已接近门槛的真问题顶过去</b>，
 * 不是重排整个结果集；权重一大就会开始制造新的误召。
 */
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    /** 是否启用向量检索（关闭时知识库为空，检索恒返回 0 条）。 */
    private boolean enabled = true;

    /** 单次检索最终注入的知识片段数。 */
    private int topK = 3;

    /**
     * 相似度下限（0~1）：<b>全量 / 后台</b>检索用（小运、管理页「全量」）。
     * <p>0.79 = 实测区间 {@code (0.777, 0.802]} 的中点。
     */
    private double minScore = 0.79;

    /**
     * 相似度下限（0~1）：<b>顾客侧</b>检索用（小发、AI 客服）。
     * <p>0.75 = 实测区间 {@code (0.724, 0.780]} 的中点。
     * <p>取值 ≤ 0 表示「未单独校准」，此时回落到 {@link #minScore}。
     */
    private double minScoreCustomer = 0.75;

    /** 文档切分粒度（字符）。 */
    private int chunkSize = 400;

    /** 相邻切片的重叠字符数，避免规则被切断。 */
    private int chunkOverlap = 60;

    /** 是否启用词面加权（P3）。关闭后完全退化为纯向量检索，用于 A/B 对照。 */
    private boolean hybridEnabled = true;

    /** 词面加权时的候选条数：先取这么多条候选再重排、过滤、截取 top-k。 */
    private int hybridCandidateK = 10;

    /** 问句的实词在「小节标题」里出现比例对应的加项上限。 */
    private double hybridTitleBoost = 0.05;

    /** 问句的实词在「小节正文」里出现比例对应的加项上限；默认 0，原因见类注释。 */
    private double hybridBodyBoost = 0.0;

    /** 取本域生效的相似度门槛。 */
    public double minScoreFor(String scope) {
        if (RagScope.CUSTOMER.equals(scope) && minScoreCustomer > 0) {
            return minScoreCustomer;
        }
        return minScore;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getMinScore() { return minScore; }
    public void setMinScore(double minScore) { this.minScore = minScore; }

    public double getMinScoreCustomer() { return minScoreCustomer; }
    public void setMinScoreCustomer(double minScoreCustomer) { this.minScoreCustomer = minScoreCustomer; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public boolean isHybridEnabled() { return hybridEnabled; }
    public void setHybridEnabled(boolean hybridEnabled) { this.hybridEnabled = hybridEnabled; }

    public int getHybridCandidateK() { return hybridCandidateK; }
    public void setHybridCandidateK(int hybridCandidateK) { this.hybridCandidateK = hybridCandidateK; }

    public double getHybridTitleBoost() { return hybridTitleBoost; }
    public void setHybridTitleBoost(double hybridTitleBoost) { this.hybridTitleBoost = hybridTitleBoost; }

    public double getHybridBodyBoost() { return hybridBodyBoost; }
    public void setHybridBodyBoost(double hybridBodyBoost) { this.hybridBodyBoost = hybridBodyBoost; }
}
