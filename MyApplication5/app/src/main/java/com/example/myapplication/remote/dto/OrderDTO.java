package com.example.myapplication.remote.dto;

import java.util.List;

/**
 * 订单（后端 OrderDTO 对应）。
 */
public class OrderDTO {
    public long id;
    public String orderNo;
    public long userId;
    public double totalAmount;
    public double payAmount;
    /** 已优惠金额（自动满减 + 券）。 */
    public double discountAmount;
    /** 使用的促销活动 ID（无则 null）。 */
    public Long promotionId;
    /** 使用的优惠券 ID（未用券则 null）。 */
    public Long userCouponId;
    public String status;
    public String statusLabel;
    public String receiverName;
    public String receiverPhone;
    public String receiverAddress;
    public String remark;
    public String createdAt;
    public String paidAt;
    public String shippedAt;
    public String completedAt;
    public String cancelledAt;
    public List<OrderItemDTO> items;

    /**
     * 该订单是否已被当前用户评价过（后端按「订单内商品是否都评过」计算）。
     * <p>
     * 前台「待评价」列表据此过滤：订单状态是「已完成」但用户已评价过时不再展示，
     * 「我的」页待评价角标同样要减掉这部分。
     */
    public Boolean reviewed;

    // ---------- 物流（发货后才有） ----------

    /** 物流公司名，未发货为 null。 */
    public String shipmentCompany;
    /** 运单号，未发货为 null。前台「已发货/待收货」列表据此显示运单号。 */
    public String trackingNo;

    // ---------- 退款（申请后才有） ----------

    /** 最新一条退款申请状态：PENDING / APPROVED / REJECTED。 */
    public String refundStatus;
    /** 退款状态中文标签（待处理 / 已同意 / 已拒绝）。 */
    public String refundStatusLabel;
    /** 用户填写的退款原因。 */
    public String refundReason;
    /** 商家拒绝时填写的理由。 */
    public String refundRejectReason;
    /** 退款申请提交时间。 */
    public String refundRequestedAt;
}
