package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品评论实体。
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 32)
    private String userName;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = new BigDecimal("5.0");

    @Column(length = 1000)
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** ---- AI 分析字段（运营管理后台显示用，无需分析时为 null） ---- */

    /** 情感标签：正面 / 中性 / 负面 / 投诉。 */
    @Column(name = "ai_sentiment", length = 16)
    private String aiSentiment;

    /** 紧急程度：普通 / 需关注 / 紧急。 */
    @Column(name = "ai_urgency", length = 16)
    private String aiUrgency;

    /** 分析理由（短文本，运营看到标签时可快速理解原因）。 */
    @Column(name = "ai_reason", length = 256)
    private String aiReason;

    /** 最近一次分析时间。 */
    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getAiSentiment() { return aiSentiment; }
    public void setAiSentiment(String aiSentiment) { this.aiSentiment = aiSentiment; }

    public String getAiUrgency() { return aiUrgency; }
    public void setAiUrgency(String aiUrgency) { this.aiUrgency = aiUrgency; }

    public String getAiReason() { return aiReason; }
    public void setAiReason(String aiReason) { this.aiReason = aiReason; }

    public LocalDateTime getAiAnalyzedAt() { return aiAnalyzedAt; }
    public void setAiAnalyzedAt(LocalDateTime aiAnalyzedAt) { this.aiAnalyzedAt = aiAnalyzedAt; }
}
