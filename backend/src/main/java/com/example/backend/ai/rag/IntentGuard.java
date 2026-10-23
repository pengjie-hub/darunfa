package com.example.backend.ai.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P2「意图闸门」：判断一句话是不是<b>知识库里不可能有答案</b>的问法。
 *
 * <h3>判据只有一条：RAG 在这句话上是否只可能帮倒忙</h3>
 * 这是本类存在的唯一理由，也是它该不该拦一条问句的唯一标准。
 * 知识库里装的是<b>制度</b>（运费多少、几天到账、券为什么不叠加），不是<b>数字</b>。
 * 所以：
 * <ul>
 *   <li>「今天有多少订单？」—— 答案是<b>一个数</b>，知识库里永远不会有，只能靠查库。
 *       可是它的字面和「订单状态有哪些」这类顾客侧小节高度相似，
 *       向量检索会照样召回一两条规则塞进上下文。模型拿到一堆「订单状态分五种…」，
 *       回答一个计数问题时既没用、还容易顺着规则答成别的东西。
 *       <b>这类问句召回得越多越坏</b>，所以应当直接空手交出去（交给工具，或让模型坦白）。</li>
 *   <li>「生鲜能退吗？」—— 答案<b>就是一条制度</b>，知识库里正好有。
 *       哪怕它字面短、向量分只有 0.7796，也<b>绝不能</b>被本闸门拦掉。</li>
 * </ul>
 * 因此闸门只拦两类问法，且<b>都必须能从「顾客侧知识库里不可能有答案」推出来</b>：
 * <ol>
 *   <li><b>平台业务数字 / 统计口径</b>（今天有多少订单、销售额是多少、环比报表）——
 *       答案是查库得来的数，知识库只有口径定义；</li>
 *   <li><b>后台运营概念</b>（后台角色权限、客服接待量、低库存补货）——
 *       这些小节全部标了 {@link RagScope#OPS_MARKER}，<b>顾客侧的候选集里根本没有它们</b>，
 *       于是任何召回都必然是一条不相关的顾客侧小节。</li>
 * </ol>
 * 反过来，凡是「顾客侧知识库里可能有答案」的问法一律放过：
 * 哪怕它字面短、向量分低（「生鲜能退吗？」= 0.7796），也绝不在这里被拦。
 * 一句话概括边界：<b>闸门问的是「这个池子里有没有可能有答案」，不是「这句话重不重要」。</b>
 * <p>
 * 注意分工：<b>运营侧小节不进顾客池，是靠 {@link RagScope} 的元数据过滤完成的，与本闸门无关。</b>
 * 本闸门补的是「过滤之后仍会误命中顾客侧小节」的缝 —— 两者解决的不是同一件事，不能互相替代。
 *
 * <h3>为什么不用模型判断意图</h3>
 * 一次意图判断要多花一次大模型调用（还多一次被限流/抖动拖垮的机会），
 * 而这里要识别的只是三类很明确的问法。用确定性规则实现：
 * <b>零额外延迟、零额外成本、行为可复现</b>，而且能在管理页里逐条解释「为什么这句被拦了」。
 * 规则是对的还是错的，看一眼正则就能判断，不需要去猜模型的意图。
 */
public final class IntentGuard {

    /**
     * 一条拦截规则。
     *
     * @param name    规则名（会显示在管理页与日志里，说明「为什么被拦」）
     * @param pattern 命中即拦截
     * @param why     这条规则在拦什么，写给下一个读代码的人看
     */
    private record Rule(String name, Pattern pattern, String why) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("聚合口径词",
                    Pattern.compile("销售额|营业额|(?:^|[^a-zA-Z])GMV([^a-zA-Z]|$)|订单量|订单数|客单价|转化率|复购率|毛利率|净利润|利润总额",
                            Pattern.CASE_INSENSITIVE),
                    "这些词问的都是一个统计数字，知识库里只有口径定义、没有数字，召回规则片段没有意义"),
            new Rule("计数问法（前导）",
                    Pattern.compile("(多少|几)[笔单件个台]?(订单|单子|商品数量|销量|库存)"),
                    "「多少/几 + 订单/销量/库存」是问个数，属于查库问题；限定词紧邻可避免误伤「多少种订单状态」这类规则问题"),
            new Rule("计数问法（后置）",
                    Pattern.compile("(订单|销量|库存|商品)[^，。？！,.:;?]{0,6}(多少|几[笔单件个台])"),
                    "同上，只是把问法倒过来写（「订单还剩几笔」），限制 6 字以内避免跨句误伤"),
            new Rule("报表与统计词",
                    Pattern.compile("统计口径|大盘|报表|日报|周报|月报|数据看板|看板|环比|同比"),
                    "这些是运营看数的说法，对应的是后台统计功能而不是平台规则"),
            new Rule("后台口径词",
                    Pattern.compile("角色|权限|接待量|响应及时率|低库存|补货|库存量"),
                    "这些概念在知识库里只存在于「运营侧」小节，顾客侧池子里根本没有，"
                            + "因此任何召回都必然是不相关的顾客侧片段 —— 不靠阈值兜底，直接跳过检索。")
    );

    private IntentGuard() {
    }

    /**
     * 判断这句话是否应该跳过知识库检索。
     *
     * @return 命中的规则名；未命中返回空串（正常走检索）
     */
    public static String matchedRule(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(query).find()) {
                return rule.name();
            }
        }
        return "";
    }

    /**
     * 供管理页展示「为什么被拦」的说明文本。
     *
     * @return 未命中返回空串；命中返回「规则名：原因」
     */
    public static String explain(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(query);
            if (matcher.find()) {
                return rule.name() + "（命中的是「" + matcher.group() + "」）：" + rule.why();
            }
        }
        return "";
    }

    /** 全部规则（管理页「查看闸门规则」用）：规则名 → 正则 + 说明。 */
    public static List<Map<String, String>> rules() {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        for (Rule rule : RULES) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", rule.name());
            item.put("pattern", rule.pattern().pattern());
            item.put("why", rule.why());
            out.add(item);
        }
        return out;
    }
}
