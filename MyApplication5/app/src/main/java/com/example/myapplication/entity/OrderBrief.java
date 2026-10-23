package com.example.myapplication.entity;

/**
 * 订单概要：整单维度的最小信息，供「收银台」（PayActivity）使用。
 * <p>
 * 为什么不复用 {@link OrderInfo}：那是**一条明细一行**的列表结构
 * （同一订单会被展开成多行，且没有整单实付金额），
 * 而下单成功后要跳收银台，需要的是「订单 id + 订单号 + 应付金额 + 状态」这一组整单信息。
 */
public class OrderBrief {

    private long orderId;
    private String orderNo;
    /** 应付金额（已扣促销优惠） */
    private double payAmount;
    /** 已优惠金额（自动满减 + 券），无优惠为 0。 */
    private double discountAmount;
    /** 中文状态标签，如「待付款」「待发货」 */
    private String status;

    public OrderBrief() {
    }

    public OrderBrief(long orderId, String orderNo, double payAmount, double discountAmount,
                      String status) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.payAmount = payAmount;
        this.discountAmount = discountAmount;
        this.status = status;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public double getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(double payAmount) {
        this.payAmount = payAmount;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
