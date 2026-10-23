package com.example.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单响应 DTO（订单主信息 + 明细列表 + 状态中文名）。
 */
public class OrderDTO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private BigDecimal discountAmount;
    private Long promotionId;
    /** 使用的用户优惠券 ID（未用券为 null）。 */
    private Long userCouponId;
    private String status;
    private String statusLabel;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private String createdAt;
    private String paidAt;
    private String shippedAt;
    private String completedAt;
    private String cancelledAt;
    private List<OrderItemDTO> items;

    /** 风险等级枚举名（LOW / MEDIUM / HIGH），未扫描时为 null。 */
    private String riskLevel;
    /** 风险评分 0-100，未扫描时为 null。 */
    private Integer riskScore;
    /** 风险扫描时间（yyyy-MM-dd HH:mm:ss），未扫描时为 null。 */
    private String riskScannedAt;

    /**
     * 该订单是否已被当前用户评价过。
     * <p>
     * 前台「待评价」列表据此过滤：订单状态虽然是「已完成」，但只要用户已评价过，
     * 就不应该再出现在待评价里，也不该计入「我的」页待评价角标。
     * 判定口径 = 订单内所有商品都已被该用户评价过（同一用户同一商品只能评论一次）。
     * 旧前端不认识本字段，多返回一个字段不影响兼容。
     */
    private Boolean reviewed;

    // ---------- 物流（发货后才有） ----------

    /** 物流公司名，未发货为 null。 */
    private String shipmentCompany;
    /** 运单号，未发货为 null。前台「已发货/待收货」列表要展示它。 */
    private String trackingNo;

    // ---------- 退款（申请后才有） ----------

    /** 最新一条退款申请的状态：PENDING / APPROVED / REJECTED，从未申请为 null。 */
    private String refundStatus;
    private String refundStatusLabel;
    /** 用户填写的退款原因。 */
    private String refundReason;
    /** 商家拒绝时填写的理由。 */
    private String refundRejectReason;
    /** 退款申请提交时间。 */
    private String refundRequestedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public String getShippedAt() { return shippedAt; }
    public void setShippedAt(String shippedAt) { this.shippedAt = shippedAt; }

    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

    public String getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }

    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getRiskScannedAt() { return riskScannedAt; }
    public void setRiskScannedAt(String riskScannedAt) { this.riskScannedAt = riskScannedAt; }

    public Boolean getReviewed() { return reviewed; }
    public void setReviewed(Boolean reviewed) { this.reviewed = reviewed; }

    public String getShipmentCompany() { return shipmentCompany; }
    public void setShipmentCompany(String shipmentCompany) { this.shipmentCompany = shipmentCompany; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public String getRefundStatusLabel() { return refundStatusLabel; }
    public void setRefundStatusLabel(String refundStatusLabel) { this.refundStatusLabel = refundStatusLabel; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public String getRefundRejectReason() { return refundRejectReason; }
    public void setRefundRejectReason(String refundRejectReason) { this.refundRejectReason = refundRejectReason; }

    public String getRefundRequestedAt() { return refundRequestedAt; }
    public void setRefundRequestedAt(String refundRequestedAt) { this.refundRequestedAt = refundRequestedAt; }
}
