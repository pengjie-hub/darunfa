package com.example.myapplication.remote.dto;

import java.util.List;

/**
 * 下单请求：支持从购物车下单或立即购买。
 */
public class CreateOrderRequest {
    public List<Long> cartItemIds;
    public Long productId;
    public Integer quantity;
    public String receiverName;
    public String receiverPhone;
    public String receiverAddress;
    /** 选用的优惠券 ID（{@code user_coupons.id}），可空。后端会校验归属/状态/门槛。 */
    public Long userCouponId;
    public String remark;
}
