package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 领取优惠券请求。
 */
public class ClaimCouponRequest {

    @NotNull(message = "请指定要领取的活动")
    private Long promotionId;

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }
}
