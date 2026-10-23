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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 后台写操作审计与幂等记录（表 {@code admin_action_logs}）。
 *
 * <h3>一张表为什么同时干两件事</h3>
 * 「这笔退款是谁批的」（审计）与「同一笔确认不要执行两次」（幂等）问的是同一行数据：
 * 谁、何时、从哪个入口、对哪个对象、用什么参数、结果如何。拆两张表只会让状态机分裂，
 * 因此审计与幂等共用本表，{@link #idempotencyKey} 的唯一约束就是幂等闸门。
 *
 * <h3>状态机（{@link Status}）</h3>
 * <pre>
 *   PROPOSED ──用户点确认──▶ CONFIRMED ──业务成功──▶ EXECUTED
 *      │                        └────业务失败──▶ FAILED（可重试项 attempt+1 后回到 CONFIRMED）
 *      ├──超过 expiresAt──▶ EXPIRED
 *      └──用户取消/关闭会话──▶ CANCELLED
 * </pre>
 * 对话入口：{@code PROPOSED} 由 AdminActionService 在出卡片时写入；页面入口没有提案，
 * 由执行器直接写 {@code CONFIRMED}（见 {@link #proposalId} 可为空）。
 *
 * <h3>三个容易踩的坑（写在这里以免下次又踩）</h3>
 * <ol>
 *   <li><b>{@code idempotencyKey} 必须可空</b>：页面入口（{@code entry=PAGE}）没有幂等键。
 *       MySQL 的 UNIQUE 索引允许多行 NULL，所以页面多次操作不会互相撞键；
 *       若图省事填空串 {@code ""}，第二次页面操作就会直接 500。因此这里
 *       <b>不加 {@code nullable=false}</b>，也绝不写默认值。</li>
 *   <li><b>{@code paramsJson}/{@code precheckJson}/{@code snapshotJson}/{@code resultJson} 用 TEXT 而非 JSON 列</b>：
 *       字段类型是 {@link String}，与既有 {@link RiskScore#getReasons()} 的存法一致；
 *       换成 MySQL JSON 列需要 {@code @JdbcTypeCode(SqlTypes.JSON)}，与 {@code ddl-auto=update} 易冲突。</li>
 *   <li><b>{@code expiresAt} 落列而不是现算</b>：有效期以后会按动作分级（退款 5 分钟、活动 15 分钟），
 *       落列才可演进；{@code created_at + 5min} 的硬编码迟早要改。</li>
 * </ol>
 */
@Entity
@Table(
        name = "admin_action_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_aal_idem", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_aal_proposal", columnList = "proposal_id"),
                @Index(name = "idx_aal_operator_time", columnList = "operator_id, created_at"),
                @Index(name = "idx_aal_target_time", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_aal_status_time", columnList = "status, created_at"),
                @Index(name = "idx_aal_action_time", columnList = "action, created_at")
        })
public class AdminActionLog {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提案 ID（{@code pa_xxxxxxxxxx}）；页面入口为空。 */
    @Column(name = "proposal_id", length = 32)
    private String proposalId;

    /** 动作枚举名（存 {@code AdminAction.name()}，本实体不依赖 ai 包，保持持久层零外部耦合）。 */
    @Column(name = "action", nullable = false, length = 40)
    private String action;

    /** 对象类型：REFUND_REQUEST / ORDER / PRODUCT / PROMOTION / ORDER_BATCH。 */
    @Column(name = "target_type", nullable = false, length = 24)
    private String targetType;

    /** 对象主键；批量主记录与「尚未创建的对象」为空。 */
    @Column(name = "target_id")
    private Long targetId;

    /** 最终参数（JSON 串）。 */
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;

    /** 参数指纹（规范化后 sha1），用于执行时 {@code PROPOSAL_MISMATCH} 校验。 */
    @Column(name = "params_digest", length = 40)
    private String paramsDigest;

    /** 预检结论（JSON 串），证明「当时是合法的」。 */
    @Column(name = "precheck_json", columnDefinition = "TEXT")
    private String precheckJson;

    /** 预检时刻的关键快照（JSON 串），含补货的 {@code expectedStock}。 */
    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    /**
     * 出卡片时的完整提案快照（{@code ActionProposal} 的 JSON 串）。
     *
     * <h3>为什么把整份提案也存一份</h3>
     * 对话当轮响应里的 {@code proposals} 只存在于内存；刷新页面后若要重新展示「待确认操作」，
     * 就必须能把同一张卡片原样重建出来。params/precheck/snapshot 三列只够「执行校验」，
     * 不足以还原 {@code fields}（服务端算好的展示字段）与 {@code risk}。
     * 因此新增本列，作为「待确认操作常驻入口」的<b>首选数据源</b>：
     * 反序列化即得到与当轮响应<b>逐字节一致</b>的提案。
     * <p>
     * 老数据没有本列（为 null）时，回退到用 params/precheck/snapshot 重新渲染
     * （见 {@code AdminActionService.rebuildProposal}），不会因此报错。
     */
    @Column(name = "proposal_json", columnDefinition = "TEXT")
    private String proposalJson;

    /**
     * 幂等键。<b>允许为空</b>，且事实上只在「执行」阶段有值：
     * <ul>
     *   <li>{@code PROPOSED}（AI 出卡片）→ <b>null</b>。同一个动作可以被多次预检（用户说两遍、
     *       卡片过期后重新预检），若预检就写键，第二次预检必然撞 {@code uk_aal_idem}，
     *       卡片会被静默丢弃；</li>
     *   <li>{@code CONFIRMED} 起 → 由执行器写入（抢占）。唯一约束在<u>这一刻</u>
     *       才成为「同一件事只能执行一次」的闸门，这也正是幂等想表达的意思；</li>
     *   <li>{@code entry=PAGE}（后台页面按钮）→ 永远没有键，只能靠状态机兜底。</li>
     * </ul>
     * 值形如 {@code refund:8:APPROVE} / {@code restock:1:7:100}。
     * <p>
     * <b>不要</b>填空串 {@code ""}：MySQL 的 UNIQUE 允许多行 NULL，但空串只能有一行，
     * 第二次页面操作就会直接 500。
     */
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    /** 状态机，见类注释。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PROPOSED;

    /** 操作人 {@code users.id}；唯一来源是请求上下文，绝不来自模型参数。 */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** 操作人角色（实时查库得到的 ADMIN/OPS/CS，仅用于审计展示，不参与鉴权）。 */
    @Column(name = "operator_role", length = 16)
    private String operatorRole;

    /** 入口：CHAT / PAGE / API。 */
    @Column(name = "entry", nullable = false, length = 16)
    private String entry = "API";

    /** 关联 {@code ai_chat_messages.id}，可回放到具体那句话。 */
    @Column(name = "chat_message_id")
    private Long chatMessageId;

    /** 执行结果（DTO 或批量的 items[]，JSON 串）。 */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    /** 失败细化码。 */
    @Column(name = "error_code", length = 40)
    private String errorCode;

    /** 失败说明（面向运营）。 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 重试次数（首次执行为 1）。 */
    @Column(name = "attempt", nullable = false)
    private Integer attempt = 1;

    /** 提案有效期，仅 PROPOSED 有意义。 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 来源 IP。 */
    @Column(name = "ip", length = 45)
    private String ip;

    /** 来源 UA。 */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /**
     * 兜底填充：以实体为准（{@code schema.sql} 里的 {@code DEFAULT CURRENT_TIMESTAMP}
     * 只对绕过 JPA 的手工 INSERT 生效，两者共存不冲突）。
     */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.status == null) {
            this.status = Status.PROPOSED;
        }
        if (this.attempt == null || this.attempt < 1) {
            this.attempt = 1;
        }
        if (this.entry == null || this.entry.isBlank()) {
            this.entry = "API";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public String getParamsDigest() { return paramsDigest; }
    public void setParamsDigest(String paramsDigest) { this.paramsDigest = paramsDigest; }

    public String getPrecheckJson() { return precheckJson; }
    public void setPrecheckJson(String precheckJson) { this.precheckJson = precheckJson; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public String getProposalJson() { return proposalJson; }
    public void setProposalJson(String proposalJson) { this.proposalJson = proposalJson; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }

    public String getEntry() { return entry; }
    public void setEntry(String entry) { this.entry = entry; }

    public Long getChatMessageId() { return chatMessageId; }
    public void setChatMessageId(Long chatMessageId) { this.chatMessageId = chatMessageId; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    /**
     * 提案/执行状态机。
     */
    public enum Status {

        /** 已出卡片，等待用户确认。 */
        PROPOSED,

        /** 用户已确认，业务执行中（执行器进入前写入，用于挡住 60 秒内的重复提交）。 */
        CONFIRMED,

        /** 业务执行成功，{@code result_json} 保存上次结果供幂等命中时直接回放。 */
        EXECUTED,

        /** 业务执行失败。 */
        FAILED,

        /** 超过 {@code expires_at} 未确认，惰性过期（无定时任务，见设计 10.2）。 */
        EXPIRED,

        /** 用户主动取消/关闭会话。 */
        CANCELLED
    }
}
