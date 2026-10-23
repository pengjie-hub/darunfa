package com.example.backend.dto;

import java.util.List;

/**
 * AI 评论分析结果响应。
 * <ul>
 *   <li>{@code reviewId} — 被分析的评论 ID</li>
 *   <li>{@code sentiment} — 情感标签：正面 / 中性 / 负面 / 投诉</li>
 *   <li>{@code urgency} — 紧急程度：普通 / 需关注 / 紧急</li>
 *   <li>{@code reason} — 短理由</li>
 *   <li>{@code drafts} — 当为代起草回复响应时，携带回复草稿；分析时为 null</li>
 * </ul>
 */
public class AiReviewAnalyzeResponse {

    private Long reviewId;
    private String sentiment;
    private String urgency;
    private String reason;
    private List<String> drafts;

    public AiReviewAnalyzeResponse() {}

    public AiReviewAnalyzeResponse(Long reviewId, String sentiment, String urgency, String reason) {
        this.reviewId = reviewId;
        this.sentiment = sentiment;
        this.urgency = urgency;
        this.reason = reason;
    }

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<String> getDrafts() { return drafts; }
    public void setDrafts(List<String> drafts) { this.drafts = drafts; }
}
