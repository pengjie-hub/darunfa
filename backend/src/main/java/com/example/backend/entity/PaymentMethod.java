package com.example.backend.entity;

/**
 * 支付方式枚举。
 */
public enum PaymentMethod {
    WECHAT("微信支付"),
    ALIPAY("支付宝"),
    BANK("银行卡"),
    CASH("现金"),
    BALANCE("余额支付");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
