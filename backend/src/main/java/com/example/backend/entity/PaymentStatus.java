package com.example.backend.entity;

/**
 * 支付状态枚举。
 */
public enum PaymentStatus {
    SUCCESS("支付成功"),
    REFUNDED("已退款"),
    FAILED("支付失败");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
