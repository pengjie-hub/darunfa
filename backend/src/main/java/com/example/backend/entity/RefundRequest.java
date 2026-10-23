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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款申请实体：用户在前台「退款/售后」提交，商家在后台审核。
 * <p>
 * 关键口径：<b>申请不等于退款</b>。提交后订单进入 {@link OrderStatus#REFUNDING}（退款中），
 * 钱、库存、优惠券都原封不动；只有商家点「同意」{@link RefundStatus#APPROVED} 时才真正执行退回。
 * 因此本表必须记录 {@link #previousStatus} —— 商家拒绝时要把订单原样恢复，
 * 否则订单会永远卡在「退款中」。
 */
@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 申请退款金额（取订单实付金额 payAmount）。 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 用户填写的退款原因。 */
    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RefundStatus status = RefundStatus.PENDING;

    /** 提交申请前的订单状态，商家拒绝时用它原样回滚。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 24)
    private OrderStatus previousStatus;

    /** 商家拒绝时填写的理由。 */
    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 商家处理（同意/拒绝）时间。 */
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    /** 处理人（后台操作者）用户 ID。 */
    @Column(name = "handled_by")
    private Long handledBy;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }

    public OrderStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(OrderStatus previousStatus) { this.previousStatus = previousStatus; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
}
