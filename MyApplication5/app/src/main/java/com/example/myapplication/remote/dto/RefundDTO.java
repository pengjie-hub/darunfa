package com.example.myapplication.remote.dto;

/**
 * 退款申请（后端 {@code RefundDTO}）。
 * <p>
 * 前台只在「申请退款」成功后用它提示用户「已提交，等待商家审核」，
 * 订单列表里的退款状态直接取 {@link OrderDTO#refundStatus} 等字段，不必再请求本对象。
 */
public class RefundDTO {

    public long id;
    public long orderId;
    public String orderNo;
    public long userId;
    public double amount;
    /** 用户填写的退款原因。 */
    public String reason;
    /** PENDING / APPROVED / REJECTED */
    public String status;
    /** 中文标签：待处理 / 已同意 / 已拒绝。 */
    public String statusLabel;
    /** 申请前的订单状态中文名，如「待发货」。 */
    public String previousStatusLabel;
    /** 商家拒绝理由。 */
    public String rejectReason;
    public String createdAt;
    public String handledAt;
}
