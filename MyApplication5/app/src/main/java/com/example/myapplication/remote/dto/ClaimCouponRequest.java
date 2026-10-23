package com.example.myapplication.remote.dto;

/**
 * 领取优惠券请求（对应后端 {@code ClaimCouponRequest}）。
 */
public class ClaimCouponRequest {

    public Long promotionId;

    public ClaimCouponRequest(Long promotionId) {
        this.promotionId = promotionId;
    }
}
