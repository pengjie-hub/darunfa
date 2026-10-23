package com.example.backend.entity;

/**
 * 退款申请状态。
 *
 * <pre>
 * PENDING(待处理) ──商家同意──▶ APPROVED(已同意，钱已退回)
 *         └────商家拒绝──▶ REJECTED(已拒绝，订单恢复申请前状态)
 * </pre>
 */
public enum RefundStatus {

    PENDING("待处理"),
    APPROVED("已同意"),
    REJECTED("已拒绝");

    private final String label;

    RefundStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
