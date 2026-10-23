package com.example.myapplication.remote.dto;

/**
 * 余额充值请求体。
 * <p>
 * {@code requestId} 是<b>客户端幂等键</b>：同一 {@code requestId} 重复提交，服务端只会入账一次。
 * 用户在「充值失败后重试同一次充值」时应复用同一个 id；一旦金额变化或充值成功，
 * 就应换一个新的 id（详见 {@code WalletActivity} 中 {@code pendingRequestId} 的三条规则）。
 * <p>
 * 金额用 {@code double} 提交，与项目既有请求 DTO（如 {@code CreateOrderRequest}）保持一致的数值风格；
 * 后端以 {@code BigDecimal} 接收。
 */
public class RechargeRequest {

    /** 充值金额（元）。后端校验：> 0、≤ 50000、最多 2 位小数。 */
    public double amount;
    /** 幂等键（UUID）。 */
    public String requestId;

    public RechargeRequest() {
    }

    public RechargeRequest(double amount, String requestId) {
        this.amount = amount;
        this.requestId = requestId;
    }
}
