package com.example.backend.dto;

import java.math.BigDecimal;

/**
 * 结算试算结果（「优惠试算」接口与下单返回值共用）。
 * <p>
 * 存在的意义：**下单前就要让用户看到钱怎么算的**。
 * 历史上满减只在 {@code POST /api/orders} 里算，前端拿不到，
 * 于是购物车和收银台显示的都是「原价」，用户以为促销没生效。
 * <p>
 * 优惠叠加规则：<b>自动满减（取最优的一个）与用户选用的券可以叠加</b>，
 * 但应付金额有下限 0（券减到负数按 0 收，不做倒找）。
 */
public class CheckoutPreviewDTO {

    /** 商品总额（未扣任何优惠）。 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 命中的自动促销活动。 */
    private Long promotionId;
    private String promotionName;
    private BigDecimal promotionDiscount = BigDecimal.ZERO;

    /** 选用的优惠券。 */
    private Long couponId;
    private String couponName;
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    /** 总优惠 = 活动优惠 + 券优惠。 */
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 应付金额 = max(0, 总额 - 总优惠)。 */
    private BigDecimal payAmount = BigDecimal.ZERO;

    /**
     * 给用户的下一步提示。
     * <p>
     * 未达满减门槛时形如「再买 ¥50.20 可享「满 100 减 10」」，
     * 已达门槛或没有可用活动时为 null（前端不显示这一行）。
     */
    private String hint;

    /** 本次试算是否用上了券（前端据此提示「券已抵扣 ¥x」）。 */
    private boolean couponApplied;

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String promotionName) { this.promotionName = promotionName; }

    public BigDecimal getPromotionDiscount() { return promotionDiscount; }
    public void setPromotionDiscount(BigDecimal promotionDiscount) { this.promotionDiscount = promotionDiscount; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public String getCouponName() { return couponName; }
    public void setCouponName(String couponName) { this.couponName = couponName; }

    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    public boolean isCouponApplied() { return couponApplied; }
    public void setCouponApplied(boolean couponApplied) { this.couponApplied = couponApplied; }
}
