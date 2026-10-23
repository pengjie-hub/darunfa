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
 * AI 运营助手的长期记忆（key-value 形式，按 userId 隔离）。
 * <p>
 * 用于持久化用户偏好与关键上下文，例如：
 * {@code pref:reply_style=简洁}、{@code context:focus=库存预警}。
 * 同一用户同一 key 唯一，重复写入为覆盖更新。
 * </p>
 */
@Entity
@Table(name = "ai_memories", indexes = {
        @Index(name = "idx_am_user_key", columnList = "user_id, mem_key", unique = true)
})
public class AiMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属用户 ID（关联 users.id）。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 记忆键，如 pref:reply_style、context:focus。 */
    @Column(name = "mem_key", nullable = false, length = 128)
    private String memKey;

    /** 记忆值（TEXT）。 */
    @Column(name = "mem_value", nullable = false, columnDefinition = "TEXT")
    private String memValue;

    /** 最后更新时间，用于排序与展示。 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    // ---------- getter / setter ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMemKey() { return memKey; }
    public void setMemKey(String memKey) { this.memKey = memKey; }

    public String getMemValue() { return memValue; }
    public void setMemValue(String memValue) { this.memValue = memValue; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
