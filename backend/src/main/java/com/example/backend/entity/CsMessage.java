package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 客服消息实体。
 * <p>
 * 一条消息的三个来源用 {@link #sender} 区分，前台与后台都靠它决定气泡的左右与样式：
 * <ul>
 *   <li>{@link #SENDER_USER} —— 客户（前台用户）发出；</li>
 *   <li>{@link #SENDER_AI} —— AI 客服自动应答；</li>
 *   <li>{@link #SENDER_AGENT} —— 人工客服（后台运营）发出。</li>
 * </ul>
 */
@Entity
@Table(name = "cs_messages")
public class CsMessage {

    public static final String SENDER_USER = "USER";
    public static final String SENDER_AI = "AI";
    public static final String SENDER_AGENT = "AGENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 发送方：USER / AI / AGENT。 */
    @Column(nullable = false, length = 16)
    private String sender;

    /** 展示名：客户昵称 / 「AI 客服」 / 客服昵称。 */
    @Column(name = "sender_name", length = 32)
    private String senderName;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isFromCustomer() {
        return SENDER_USER.equals(sender);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
