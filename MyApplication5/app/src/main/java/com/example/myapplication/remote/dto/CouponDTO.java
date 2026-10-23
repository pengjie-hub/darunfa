package com.example.myapplication.remote.dto;

/**
 * 用户优惠券 DTO（对应后端 {@code CouponDTO}）。
 * <p>
 * 三态：{@code UNUSED} 未使用 / {@code USED} 已使用 / {@code EXPIRED} 已过期。
 * 后端已把「过期」算好（拿 expireTime 和当前时间比），前端不要自己再判断一次，
 * 否则两边时区或时间口径不一致就会出现「列表说未使用、点进去说已过期」。
 */
public class CouponDTO {

    public long id;
    /** 来源促销活动 ID。 */
    public long promotionId;
    public String name;
    /** FULL_REDUCTION / DISCOUNT。 */
    public String type;
    /** 满减 / 折扣。 */
    public String typeLabel;
    public double threshold;
    public double discount;
    /** 券面规则文案，如「满 ¥100 减 ¥10」。 */
    public String ruleText;
    /** UNUSED / USED / EXPIRED。 */
    public String status;
    /** 未使用 / 已使用 / 已过期。 */
    public String statusLabel;
    public String expireTime;
    public String claimedAt;
    public String usedAt;
    public Long orderId;
    /** 眼下可用于结算。 */
    public boolean usable;

    public boolean isUnused() {
        return "UNUSED".equals(status);
    }
}
