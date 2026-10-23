package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * AI 运营助手对话历史消息（按 userId 隔离，跨会话持久化）。
 * <p>
 * 每轮「用户提问 → AI 答复」保存两条记录（role=user / role=assistant），
 * 下次提问时抽取最近若干轮注入上下文，让 AI 具备跨会话连贯性。
 * </p>
 */
@Entity
@Table(name = "ai_chat_messages", indexes = {
        @Index(name = "idx_acm_user_created", columnList = "user_id, created_at")
})
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属用户 ID（关联 users.id），实现记忆按用户隔离。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 消息角色：user / assistant。 */
    @Column(nullable = false, length = 16)
    private String role;

    /** 消息内容（TEXT，完整保存原始文本）。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 创建时间，用于排序与展示。 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ---------- getter / setter ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
