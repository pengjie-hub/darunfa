package com.example.backend.dto;

import java.math.BigDecimal;

/**
 * 余额充值请求体。
 * <p>
 * {@code requestId} 为客户端幂等键：同一 requestId 重放不会重复入账；
 * 为空时服务端生成 UUID 兜底（此时天然不幂等，仅用于调试）。
 * <p>
 * 金额校验统一放在 {@code WalletService}（不在此用 {@code @Valid} 重复校验，避免两处文案不一致）。
 */
public class RechargeRequest {

    private BigDecimal amount;
    private String requestId;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
