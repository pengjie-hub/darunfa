package com.example.backend.dto;

import java.math.BigDecimal;

/**
 * 退款申请 DTO（后台退款管理列表用）。
 * <p>
 * 比实体多带了「申请人」「商品摘要」等后台审核时真正需要看的字段，
 * 后台不必再为每一行回查用户表与订单明细。
 */
public class RefundDTO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 申请人昵称（为空回退用户名）。 */
    private String userName;
    private String userPhone;
    private BigDecimal amount;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String statusLabel;
    /** 申请前的订单状态（拒绝后会恢复到这个状态）。 */
    private String previousStatus;
    private String previousStatusLabel;
    private String rejectReason;
    private String createdAt;
    private String handledAt;
    /** 商品摘要，如「伊利牛奶 x2，金龙鱼油 x1」。 */
    private String products;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getPreviousStatusLabel() { return previousStatusLabel; }
    public void setPreviousStatusLabel(String previousStatusLabel) { this.previousStatusLabel = previousStatusLabel; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getHandledAt() { return handledAt; }
    public void setHandledAt(String handledAt) { this.handledAt = handledAt; }

    public String getProducts() { return products; }
    public void setProducts(String products) { this.products = products; }
}
