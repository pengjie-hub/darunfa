package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 订单风险评分记录。
 * <p>
 * 一笔订单允许有多条评分记录（每次扫描写一条），最新一条以 {@code createdAt} 最大为准。
 * 设计上每笔订单保留一条"当前评分"，便于历史回看与对比；查询时取该 orderId 下 {@code createdAt} 最大者。
 * </p>
 *
 * <p>字段：
 * <ul>
 *   <li>{@code orderId}     — 关联订单 ID</li>
 *   <li>{@code score}       — 风险分 0-100，越大越危险</li>
 *   <li>{@code level}       — LOW / MEDIUM / HIGH</li>
 *   <li>{@code reasons}     — JSON 字符串，存异常点数组</li>
 *   <li>{@code suggestedAction} — 处置建议（放行 / 人工审核 / 暂停发货）</li>
 *   <li>{@code source}      — 评分来源（RULE / LLM / HYBRID）</li>
 *   <li>{@code isRead}      — 运营是否已查看（用于"未读红点"）</li>
 *   <li>{@code createdAt}   — 评分时间</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "risk_scores", indexes = {
        @Index(name = "idx_rs_order", columnList = "order_id"),
        @Index(name = "idx_rs_level", columnList = "level"),
        @Index(name = "idx_rs_score", columnList = "score")
})
public class RiskScore {

    /** 评分来源 */
    public enum Source {
        RULE,  // 纯规则引擎
        LLM,   // 纯大模型（一般不会单独出现，失败时降级用）
        HYBRID // 规则 + 大模型综合
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 0-100，越大越危险 */
    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel level;

    /** JSON 字符串，例如 [{"code":"AMOUNT_HIGH","message":"金额超过历史 P95"}] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "suggested_action", nullable = false, length = 32)
    private String suggestedAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = Boolean.FALSE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isRead == null) isRead = Boolean.FALSE;
    }

    // ---------- getter / setter ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public RiskLevel getLevel() { return level; }
    public void setLevel(RiskLevel level) { this.level = level; }

    public String getReasons() { return reasons; }
    public void setReasons(String reasons) { this.reasons = reasons; }

    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}