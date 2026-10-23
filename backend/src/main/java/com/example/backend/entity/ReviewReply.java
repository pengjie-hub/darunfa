package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 评论回复实体（同时承载「用户追评」与「商家回复」，靠 {@link #replyType} 区分）。
 */
@Entity
@Table(name = "review_replies")
public class ReviewReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 32)
    private String userName;

    @Column(nullable = false, length = 1000)
    private String content;

    /**
     * 回复类型：用户追评 / 商家回复。
     * <p>
     * 历史数据该列为 null（字段是后加的，ddl-auto=update 不会回填），
     * 语义上等价于 {@link ReplyType#USER}，读取处统一用 {@link #replyTypeOrDefault()} 兜底。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reply_type", length = 16)
    private ReplyType replyType = ReplyType.USER;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.replyType == null) {
            this.replyType = ReplyType.USER;
        }
    }

    /** 永不返回 null 的类型读取（旧数据无该列时为 USER）。 */
    public ReplyType replyTypeOrDefault() {
        return replyType == null ? ReplyType.USER : replyType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ReplyType getReplyType() { return replyType; }
    public void setReplyType(ReplyType replyType) { this.replyType = replyType; }

    /** 供 JSON 直接展示的中文类型名。 */
    public String getReplyTypeLabel() { return replyTypeOrDefault().getLabel(); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
