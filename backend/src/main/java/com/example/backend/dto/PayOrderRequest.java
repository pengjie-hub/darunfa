package com.example.backend.dto;

/**
 * 订单支付请求体：{@code method} 为支付方式名（WECHAT/ALIPAY/BANK/CASH/BALANCE）。
 * <p>
 * 缺省（含不传 body）时向后兼容按微信支付处理，由 {@code OrderController.parseMethod} 兜底。
 */
public class PayOrderRequest {

    private String method;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
