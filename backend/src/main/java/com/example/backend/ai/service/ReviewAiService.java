package com.example.backend.ai.service;

import com.example.backend.ai.assistant.ReviewAgent;
import com.example.backend.common.BusinessException;
import com.example.backend.entity.Review;
import com.example.backend.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台「评论管理」的 AI 能力：评论情感分析 + 客服回复草稿。
 *
 * <h3>改造说明</h3>
 * 原来直接 new 一个 {@code DeepSeekClient} 手拼 {@code List<Map<String,String>>} 消息再发 HTTP，
 * 现在改为调用 {@link ReviewAgent}（{@code @AiService}）。提示词、模型、超时、重试全部由配置与注解管理，
 * 本类只保留<b>两件框架不该管的事</b>：
 * <ol>
 *   <li>把 {@link Review} 领域对象渲染成给模型看的上下文文本；</li>
 *   <li>把模型返回的文本做<b>容错解析</b>并落库（写 {@code aiSentiment / aiUrgency / aiReason / aiAnalyzedAt}）。</li>
 * </ol>
 *
 * <h3>为什么保留容错解析</h3>
 * 模型偶发会返回 ```json 围栏、或把「负面」写成「负」、把「紧急」写成「高」。
 * 旧实现为此写了正则截取与枚举模糊匹配，这次<b>原样保留</b> ——
 * 让「偶发不规范」降级为「标签略微归一」，而不是把 502 甩给运营。
 */
@Service
public class ReviewAiService {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*?\\}");
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[[\\s\\S]*?\\]");
    private static final Pattern KV_PAIR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
    private static final Pattern ARRAY_ITEM = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"");

    private final ReviewAgent reviewAgent;
    private final ReviewRepository reviewRepository;

    public ReviewAiService(ReviewAgent reviewAgent, ReviewRepository reviewRepository) {
        this.reviewAgent = reviewAgent;
        this.reviewRepository = reviewRepository;
    }

    // =====================================================================
    //  1. 分析单条评论（情感 + 紧急度）
    // =====================================================================

    /**
     * 分析一条评论的情感与紧急度，结果写回实体，供后台列表直接展示（无需每次再调 AI）。
     */
    @Transactional
    public Review analyze(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));

        String raw;
        try {
            raw = reviewAgent.analyze(buildReviewContext(review, "请分析以下商品评论："));
        } catch (Exception e) {
            throw new BusinessException(502, "AI 分析失败: " + e.getMessage());
        }

        Map<String, String> parsed = parseJsonObject(raw);
        if (!parsed.containsKey("sentiment") || !parsed.containsKey("urgency")) {
            throw new BusinessException(502, "AI 返回格式异常: " + safe(raw));
        }
        review.setAiSentiment(validateSentiment(parsed.get("sentiment")));
        review.setAiUrgency(validateUrgency(parsed.get("urgency")));
        review.setAiReason(truncate(parsed.getOrDefault("reason", ""), 240));
        review.setAiAnalyzedAt(java.time.LocalDateTime.now());
        return reviewRepository.save(review);
    }

    /**
     * 批量分析尚未分析过的评论（最多 limit 条），返回被成功分析的 reviewId 列表。
     * <p>单条失败不影响其余 —— 运营点一次「批量分析」，不应因为某条评论触发了模型异常而全部作废。
     */
    @Transactional
    public List<Long> batchAnalyze(int limit) {
        int max = Math.min(Math.max(limit, 1), 200);
        List<Review> pending = reviewRepository.findTop200ByAiAnalyzedAtIsNullOrderByIdDesc();
        List<Long> done = new ArrayList<>();
        for (Review review : pending) {
            if (done.size() >= max) break;
            try {
                analyze(review.getId());
                done.add(review.getId());
            } catch (Exception ignore) {
                // 单条失败继续下一条
            }
        }
        return done;
    }

    // =====================================================================
    //  2. 生成客服回复草稿
    // =====================================================================

    /** 根据评论生成 2 条客服回复草稿（不写回，由运营自行采纳）。 */
    public List<String> draftReply(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));

        String raw;
        try {
            raw = reviewAgent.draftReply(
                    buildReviewContext(review, "用户评论与商品上下文如下，请生成 2 个客服回复草稿："));
        } catch (Exception e) {
            throw new BusinessException(502, "AI 生成失败: " + e.getMessage());
        }
        return parseJsonStringArray(raw);
    }

    // =====================================================================
    //  上下文渲染
    // =====================================================================

    /** 把评论渲染成提示词里的用户消息：指令 + 用户名 + 评分 + 正文。 */
    private String buildReviewContext(Review review, String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append(instruction).append("\n");
        if (review.getUserName() != null) sb.append("用户名：").append(review.getUserName()).append("\n");
        sb.append("评分：").append(review.getRating()).append("/5\n");
        sb.append("评论内容：\n").append(review.getContent() == null ? "" : review.getContent()).append("\n");
        return sb.toString();
    }

    // =====================================================================
    //  容错解析 / 归一
    // =====================================================================

    private static Map<String, String> parseJsonObject(String s) {
        if (s == null) return Map.of();
        Matcher m = JSON_OBJECT.matcher(s);
        if (!m.find()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        Matcher kv = KV_PAIR.matcher(m.group());
        while (kv.find()) {
            out.put(kv.group(1), kv.group(2).replace("\\\"", "\""));
        }
        return out;
    }

    private static List<String> parseJsonStringArray(String s) {
        if (s == null) return List.of();
        Matcher m = JSON_ARRAY.matcher(s);
        if (!m.find()) return List.of(s.trim());
        List<String> out = new ArrayList<>();
        Matcher item = ARRAY_ITEM.matcher(m.group());
        while (item.find()) out.add(item.group(1).replace("\\\"", "\""));
        return out.isEmpty() ? List.of(s.trim()) : out;
    }

    private static String validateSentiment(String raw) {
        if (raw == null) return "中性";
        String s = raw.trim();
        return switch (s) {
            case "正面", "中性", "负面", "投诉" -> s;
            default -> {
                if (s.contains("投诉")) yield "投诉";
                if (s.contains("负") || s.contains("差")) yield "负面";
                if (s.contains("正") || s.contains("好") || s.contains("满意")) yield "正面";
                yield "中性";
            }
        };
    }

    private static String validateUrgency(String raw) {
        if (raw == null) return "普通";
        String s = raw.trim();
        return switch (s) {
            case "普通", "需关注", "紧急" -> s;
            default -> {
                if (s.contains("紧")) yield "紧急";
                if (s.contains("关")) yield "需关注";
                yield "普通";
            }
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
