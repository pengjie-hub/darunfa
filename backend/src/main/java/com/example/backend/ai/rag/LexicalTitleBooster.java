package com.example.backend.ai.rag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * P3「词面加权」的打分器：纯字符二元组重叠，不依赖任何外部资源。
 *
 * <h3>它要解决什么问题</h3>
 * 向量检索有一个洗不掉的弱点：<b>口语短问句整体偏低</b>。
 * 用户问「怎么转人工？」，目标小节就叫「在线客服与转人工」——词几乎一模一样，
 * 可余弦相似度只有 <b>0.769</b>；而「今天天气怎么样」这种完全无关的问题却有 <b>0.777</b>。
 * 原因是 BGE 这类模型把整句话映射成一个稠密向量，
 * 五个字的短问句信息量太少，向量天然偏向「平均语义」，
 * 于是「像不像一句话」压过了「是不是在问同一件事」。
 * <p>
 * 而「是不是同一个词」恰恰是词面匹配最擅长的。所以这里补一路<b>词面证据</b>：
 * 算出问句与小节标题/正文的字面重合度，作为向量分之外的加项。
 *
 * <h3>为什么用「内容二元组」而不是分词或全字符</h3>
 * <ul>
 *   <li><b>不引第三方分词器</b>：平台知识只有几十个片段，为了算一个重叠度再引入一套词典与模型，
 *       成本远大于收益；中文相邻二字本身就携带了大部分区分度。</li>
 *   <li><b>必须滤掉虚词二元组</b>：这是本类最关键的一步。
 *       「怎么转人工？」的二元组是 {怎么, 么转, 转人, 人工}，其中只有 <b>转人 / 人工</b> 是实词；
 *       而「今天天气怎么样」的二元组 {今天, 天天, 天气, 气怎, 怎么, 么样} 里
 *       <b>没有一个实词</b> —— 它之所以能拿到 0.777，正是因为「按天统计…怎么算」这条标题里
 *       也有「怎么」。如果不过滤虚词，词面证据会把这类伪相关<b>越推越高</b>，
 *       等于给已知的病再加一层病。滤掉之后它的词面得分是 <b>0</b>，一分都拿不到。</li>
 * </ul>
 * <p>
 * 实现在 {@link #STOP_CHARS} 中显式列出虚词字符表 —— 只有<b>两个字符都是实词</b>的二元组才算证据。
 * 那些「几乎总是虚词」的字符（的/了/吗/怎么/多少/我/你…）进表；
 * 有实义嫌疑的一律不进表，宁可留着噪声（例如 {@code 会} 不进表，否则「会员」会被误杀；
 * {@code 有} 不进表，否则「有效期」会被误杀）。这张表是<b>可解释、可复核</b>的 ——
 * 想调它只需回答一个问题：「这个字单独看，是不是几乎从不承担主题信息？」
 *
 * <h3>只用「覆盖度」不用「绝对条数」</h3>
 * 得分定义为 <b>命中数 ÷ 问句自身的内容二元组个数</b>，即问句里有<b>多大比例</b>的词在小节里出现过。
 * 这样长问句不会因为「词多所以命中的绝对数多」而占便宜，
 * 而「问句的每个实词都在这个小节里出现过」这种强证据才拿满分。
 */
public final class LexicalTitleBooster {

    /**
     * 虚词字符表：只由这些字符构成的二元组不携带主题信息，直接丢弃。
     * <p>取舍标准见类注释 —— 有实义嫌疑的字一律<b>不</b>放进来，避免误杀「会员」「有货」「有效期」这类词。
     */
    private static final String STOP_CHARS =
            "的着了着过地得吗呢吧啊呀嘛哦嗯啦"
            + "和与及或就都还也又很太而不被把给对从为在之所其此该每各"
            + "请问谢我你他她它们这那哪是没无么什么怎多少可以能要想一下个真好么";

    private static final Set<Character> STOP = buildStopSet();

    private LexicalTitleBooster() {
    }

    /**
     * 抽取文本中的「内容二元组」。
     * <p>先在标点/空白处切成若干连续实字片段，<b>只在片段内部</b>取相邻二字的组合 ——
     * 否则「生鲜，能退吗」会拼出一个现实中不存在的「鲜能」。
     */
    public static Set<String> contentBigrams(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                run.append(c);
            } else {
                flushRun(run, out);
                run.setLength(0);
            }
        }
        flushRun(run, out);
        return out;
    }

    /**
     * 词面加权值：命中标题给 {@code titleBoost}，命中正文给更小的 {@code bodyBoost}。
     *
     * <p>标题权重高于正文是有意的：标题是「这条规则在讲什么」的浓缩，
     * 「怎么转人工」命中标题「在线客服与<b>转人工</b>」是强证据；
     * 正文则可能只是顺带提到某个词（「生鲜」在退货规则正文里出现，但那确实是我们要的答案，
     * 所以正文权重不为零，只是更小）。
     *
     * @param queryBigrams 问句的内容二元组（由 {@link #contentBigrams} 算出，避免重复计算）
     * @param title        小节标题
     * @param body         小节正文（不含标题）
     * @return 加在向量分之上的增量，范围 {@code [0, titleBoost + bodyBoost]}
     */
    public static double bonus(Set<String> queryBigrams, String title, String body,
                               double titleBoost, double bodyBoost) {
        if (queryBigrams.isEmpty()) {
            return 0d;
        }
        double bonus = 0d;
        if (titleBoost > 0) {
            bonus += titleBoost * coverage(queryBigrams, contentBigrams(title));
        }
        if (bodyBoost > 0) {
            bonus += bodyBoost * coverage(queryBigrams, contentBigrams(body));
        }
        return bonus;
    }

    /** 问句内容二元组中，有多大比例出现在候选文本里（0~1）。 */
    public static double coverage(Set<String> queryBigrams, Set<String> candidateBigrams) {
        if (queryBigrams.isEmpty() || candidateBigrams.isEmpty()) {
            return 0d;
        }
        int hit = 0;
        for (String bigram : queryBigrams) {
            if (candidateBigrams.contains(bigram)) {
                hit++;
            }
        }
        return (double) hit / queryBigrams.size();
    }

    /** 命中的内容二元组，用于把「为什么加分」写进日志与管理页。 */
    public static Set<String> hits(Set<String> queryBigrams, String text) {
        Set<String> hits = new LinkedHashSet<>();
        Set<String> candidate = contentBigrams(text);
        for (String bigram : queryBigrams) {
            if (candidate.contains(bigram)) {
                hits.add(bigram);
            }
        }
        return hits;
    }

    // ==================== 内部实现 ====================

    private static void flushRun(StringBuilder run, Set<String> out) {
        int n = run.length();
        if (n == 0) {
            return;
        }
        if (n == 1) {
            // 单字片段（「退」「货」）：没有相邻字可组，保留单字本身
            if (!isStop(run.charAt(0))) {
                out.add(run.toString());
            }
            return;
        }
        for (int i = 0; i + 1 < n; i++) {
            if (!isStop(run.charAt(i)) && !isStop(run.charAt(i + 1))) {
                out.add(run.substring(i, i + 2));
            }
        }
    }

    private static boolean isStop(char c) {
        return STOP.contains(c);
    }

    private static Set<Character> buildStopSet() {
        Set<Character> set = new LinkedHashSet<>();
        for (int i = 0; i < STOP_CHARS.length(); i++) {
            set.add(STOP_CHARS.charAt(i));
        }
        return set;
    }
}
