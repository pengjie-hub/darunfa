package com.example.backend.dto;

import java.math.BigDecimal;

/**
 * 订单明细响应 DTO（含商品快照）。
 */
public class OrderItemDTO {

    private Long id;
    private Long productId;

    private String productName;
    private String productImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;

    /**
     * 这一件商品是否已被当前用户评价过（仅在「我的订单」这类按用户查询的接口里赋值）。
     * <p>
     * 前台会把订单摊平成「一行一件商品」展示，所以待评价的过滤必须细到商品这一级：
     * 一单买了两件、只评了一件时，评过的那行要立刻消失，另一行继续提示去评价。
     * 未按用户查询的场景（如后台订单列表）该字段为 null，前端按「未评价」处理即可。
     */
    private Boolean reviewed;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public Boolean getReviewed() { return reviewed; }
    public void setReviewed(Boolean reviewed) { this.reviewed = reviewed; }
}
