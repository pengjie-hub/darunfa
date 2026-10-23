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
 * 余额流水实体：每一笔余额变动（初始赠送/充值/余额支付/退款返现）都必须落一条流水。
 * <p>
 * <b>为什么必须有流水</b>：只加一列 {@code users.balance} 会丢审计、丢幂等、丢对账能力 ——
 * 流水是资金可追溯、可对账、可幂等重放的凭据。全部写路径统一经
 * {@link com.example.backend.service.WalletService} 产生，禁止绕过。
 * <p>
 * <b>有符号金额</b>：{@code amount} 正=入账、负=出账。方向已由 {@code type} 表达，
 * 不再冗余方向字段。对账恒等式：{@code SUM(amount) == users.balance}。
 * <p>
 * <b>幂等键</b>：{@code biz_no} 建唯一索引（{@code unique=true}），取值固定为
 * {@code INIT-{userId}} / {@code RECHARGE-{requestId}} / {@code ORDER-{orderId}-PAY} /
 * {@code ORDER-{orderId}-REFUND}，是资金操作幂等重放的最终保证。
 */
@Entity
@Table(name = "balance_transactions")
public class BalanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BalanceTransactionType type;

    /** 有符号金额：正=入账，负=出账。 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 操作后余额快照（必须在锁内读取最新余额后写入，保证权威且可对账）。 */
    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    /** 业务幂等键（唯一索引）。 */
    @Column(name = "biz_no", nullable = false, unique = true, length = 64)
    private String bizNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BalanceTransactionType getType() { return type; }
    public void setType(BalanceTransactionType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
