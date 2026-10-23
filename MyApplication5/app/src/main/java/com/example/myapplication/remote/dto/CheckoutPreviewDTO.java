package com.example.myapplication.remote.dto;

/**
 * 结算试算结果 DTO（对应后端 {@code CheckoutPreviewDTO}）。
 * <p>
 * 收银台与购物车靠它把优惠明细显示出来：
 * {@code 商品总额 - (活动优惠 + 券优惠) = 应付金额}。
 * 下单后后端会按同一套算法再算一次，两处口径一致。
 */
public class CheckoutPreviewDTO {

    /** 商品总额（未扣优惠）。 */
    public double totalAmount;

    /** 命中的自动促销活动。 */
    public Long promotionId;
    public String promotionName;
    public double promotionDiscount;

    /** 选用的优惠券。 */
    public Long couponId;
    public String couponName;
    public double couponDiscount;

    /** 总优惠 = 活动优惠 + 券优惠。 */
    public double discountAmount;
    /** 应付金额 = max(0, 总额 - 总优惠)。 */
    public double payAmount;
    /** 未达满减门槛时的提示，如「再买 ¥50.5 可享「满 100 减 10」」；无则为 null。 */
    public String hint;
    public boolean couponApplied;
}
