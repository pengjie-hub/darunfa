package com.example.backend.entity;

/**
 * 订单状态枚举，描述订单的合法流转链路。
 *
 * <pre>
 * PENDING_PAYMENT(待付款) ──支付──▶ PAID(待发货) ──发货──▶ SHIPPED(待收货) ──确认──▶ COMPLETED(已完成)
 *        │                        │
 *        └────────取消────────────┴──────────取消────────▶ CANCELLED(已取消)
 *
 * 已付款及之后的订单（PAID/SHIPPED/COMPLETED）可申请退款：
 *        任意上述状态 ──用户申请──▶ REFUNDING(退款中) ──商家同意──▶ REFUNDED(已退款)
 *                                          └──商家拒绝──▶ 恢复申请前的状态
 * </pre>
 */
public enum OrderStatus {

    PENDING_PAYMENT("待付款"),
    PAID("待发货"),
    SHIPPED("待收货"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    /** 用户已提交退款申请，等待商家审核（此时钱未退、库存未回补）。 */
    REFUNDING("退款中"),
    /** 商家已同意退款，钱已退回。 */
    REFUNDED("已退款");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
