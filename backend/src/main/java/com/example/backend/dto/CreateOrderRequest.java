package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 下单请求：支持「从购物车下单」或「立即购买」两种方式，二者取其一。
 */
public class CreateOrderRequest {

    /** 从购物车下单：勾选的购物车项 ID 列表。 */
    private List<Long> cartItemIds;

    /** 立即购买：商品 ID。 */
    private Long productId;

    /** 立即购买：数量。 */
    private Integer quantity;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /**
     * 选用的优惠券 ID（{@code user_coupons.id}），可空。
     * <p>
     * 服务端会校验「归属当前用户 + 未使用 + 未过期 + 已达门槛」，
     * 任何一条不满足都直接让下单失败，绝不静默忽略。
     */
    private Long userCouponId;

    private String remark;

    public List<Long> getCartItemIds() { return cartItemIds; }
    public void setCartItemIds(List<Long> cartItemIds) { this.cartItemIds = cartItemIds; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
