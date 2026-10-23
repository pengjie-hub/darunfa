package com.example.backend.entity;

/**
 * 余额流水类型枚举。
 * <p>
 * {@code INIT} 初始赠送 / {@code RECHARGE} 充值 / {@code PAY} 余额支付（出账）/ {@code REFUND} 退款返现。
 */
public enum BalanceTransactionType {

    INIT("初始赠送"),
    RECHARGE("充值"),
    PAY("余额支付"),
    REFUND("退款返现");

    private final String label;

    BalanceTransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
