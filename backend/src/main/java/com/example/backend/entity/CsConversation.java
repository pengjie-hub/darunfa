package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 客服会话实体。
 * <p>
 * 一个前台用户与客服之间只保留<b>一条</b>会话（按 {@code user_id} 唯一），
 * 好处是后台客服打开某个客户时能看到完整的历史上下文，不会散落成几十条短会话。
 * 用户重新进入客服页时复用同一条，只把 {@link #status} 从 {@code CLOSED} 拉回 {@code AI}。
 * <p>
 * 状态语义：
 * <ul>
 *   <li>{@link #STATUS_AI} —— 由 AI 自动应答（默认状态）；</li>
 *   <li>{@link #STATUS_HUMAN} —— 已转人工，后台客服在跟进；</li>
 *   <li>{@link #STATUS_CLOSED} —— 用户已结束会话（再次发消息会重新激活）。</li>
 * </ul>
 */
@Entity
@Table(name = "cs_conversations")
public class CsConversation {

    public static final String STATUS_AI = "AI";
    public static final String STATUS_HUMAN = "HUMAN";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话所属的前台用户 ID。 */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 用户显示名快照（昵称优先），避免每次查 user 表。 */
    @Column(name = "user_name", length = 32)
    private String userName;

    /** 会话状态：AI / HUMAN / CLOSED。 */
    @Column(nullable = false, length = 16)
    private String status = STATUS_AI;

    /** 最后一条消息摘要（会话列表直接展示，不用回表查消息）。 */
    @Column(name = "last_message", length = 200)
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 后台未读条数：用户每发一条 +1，客服打开会话后清零。 */
    @Column(name = "unread_for_admin", nullable = false)
    private Integer unreadForAdmin = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = STATUS_AI;
        if (this.unreadForAdmin == null) this.unreadForAdmin = 0;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public Integer getUnreadForAdmin() { return unreadForAdmin; }
    public void setUnreadForAdmin(Integer unreadForAdmin) { this.unreadForAdmin = unreadForAdmin; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
