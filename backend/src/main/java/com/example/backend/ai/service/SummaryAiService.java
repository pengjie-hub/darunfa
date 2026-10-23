package com.example.backend.ai.service;

import com.example.backend.ai.assistant.ReviewAgent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 前台「AI 总结评论」服务。
 *
 * <h3>口径约束（本次改造原样保留）</h3>
 * 商品评论下有两类文字：用户自己写的评价，以及商家/客服的回复。
 * AI 总结只应反映<b>用户的真实体验</b> —— 把商家那句「我们会尽快改进」总结成
 * 「用户反馈会改进」是严重失真的。因此这里做两层防护：
 * <ol>
 *   <li><b>入参侧过滤</b>：带商家/客服回复标记的条目直接丢弃（{@link #stripNonUserLines}）；</li>
 *   <li><b>提示词侧约束</b>：{@code AiPrompts.REVIEW_SUMMARY} 里明确告知模型「输入是用户评价，
 *       带【商家回复】等标记的内容必须完全忽略」。</li>
 * </ol>
 *
 * <p>改造点仅在底层调用：从「手写 HTTP 调 DeepSeek」换成 {@code ReviewAgent.summarize(...)}，
 * 业务口径与过滤规则一行未动。</p>
 */
@Service
public class SummaryAiService {

    /**
     * 非用户内容的前缀/包含标记（命中即丢弃）。
     * <p>前端目前只上报用户评价，这里是防御性兜底：将来若有人把整段对话（含商家回复）直接塞进来，
     * 也不会污染总结结果。
     */
    private static final String[] NON_USER_MARKERS = {
            "【商家回复】", "【客服回复】", "【商家】", "【客服】",
            "商家回复：", "商家回复:", "客服回复：", "客服回复:", "官方客服：", "官方客服:"
    };

    private final ReviewAgent reviewAgent;

    public SummaryAiService(ReviewAgent reviewAgent) {
        this.reviewAgent = reviewAgent;
    }

    /**
     * 根据商品名称与<b>用户评价</b>生成总结。
     *
     * @return AI 生成的总结文本（200 字以内）
     */
    public String summarize(String productName, List<String> reviews) {
        return reviewAgent.summarize(buildUserPrompt(productName, reviews));
    }

    private String buildUserPrompt(String productName, List<String> reviews) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下【用户评价】生成总结（其中不含商家回复）：\n");
        if (productName != null && !productName.isBlank()) {
            sb.append("商品名称：").append(productName).append("\n");
        }
        sb.append("用户评价列表：\n");
        int idx = 1;
        for (String review : reviews) {
            if (review == null || review.isBlank()) continue;
            sb.append(idx++).append(". ").append(review.trim()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 从评论文本列表中剔除「商家回复 / 客服回复」性质的条目。
     *
     * @return 只含用户评价的新列表
     */
    public static List<String> stripNonUserLines(List<String> reviews) {
        if (reviews == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String r : reviews) {
            if (r == null || r.isBlank()) continue;
            String t = r.trim();
            boolean nonUser = false;
            for (String marker : NON_USER_MARKERS) {
                if (t.contains(marker)) {
                    nonUser = true;
                    break;
                }
            }
            if (!nonUser) out.add(r);
        }
        return out;
    }
}
