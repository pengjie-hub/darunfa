package com.example.backend.dto;

import com.example.backend.entity.CouponStatus;
import com.example.backend.entity.PromotionType;
import com.example.backend.entity.UserCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户优惠券展示 DTO。
 * <p>
 * 状态是「三态」：{@code UNUSED}（未使用）/ {@code USED}（已使用）/ {@code EXPIRED}（已过期）。
 * 后两者在实体里只存了「已使用」，过期是拿 {@code expireTime} 和当前时间比出来的，
 * 所以这里由 DTO 负责把两态合成为给人看的三态。
 */
public class CouponDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    private Long promotionId;
    private String name;
    /** FULL_REDUCTION / DISCOUNT */
    private String type;
    /** 满减 / 折扣 */
    private String typeLabel;
    private BigDecimal threshold;
    private BigDecimal discount;
    /** 规则文案，如「满 ¥100 减 ¥10」「9折」。 */
    private String ruleText;
    /** UNUSED / USED / EXPIRED */
    private String status;
    /** 未使用 / 已使用 / 已过期 */
    private String statusLabel;
    private String expireTime;
    private String claimedAt;
    private String usedAt;
    private Long orderId;
    /** 眼下可用于结算（未使用且未过期）。 */
    private boolean usable;

    public static CouponDTO from(UserCoupon c) {
        CouponDTO d = new CouponDTO();
        d.id = c.getId();
        d.promotionId = c.getPromotionId();
        d.name = c.getName();
        d.type = c.getType() == null ? null : c.getType().name();
        d.typeLabel = c.getType() == null ? "" : c.getType().getLabel();
        d.threshold = c.getThreshold();
        d.discount = c.getDiscount();
        d.ruleText = buildRule(c);
        boolean expired = c.getStatus() == CouponStatus.UNUSED && c.isExpired();
        if (c.getStatus() == CouponStatus.USED) {
            d.status = "USED";
            d.statusLabel = "已使用";
        } else if (expired) {
            d.status = "EXPIRED";
            d.statusLabel = "已过期";
        } else {
            d.status = "UNUSED";
            d.statusLabel = "未使用";
        }
        d.usable = "UNUSED".equals(d.status);
        d.expireTime = fmt(c.getExpireTime());
        d.claimedAt = fmt(c.getClaimedAt());
        d.usedAt = fmt(c.getUsedAt());
        d.orderId = c.getOrderId();
        return d;
    }

    /** 券面规则文案。 */
    private static String buildRule(UserCoupon c) {
        boolean discountType = c.getType() == PromotionType.DISCOUNT;
        if (discountType) {
            return toZhe(c.getDiscount());
        }
        boolean hasThreshold = c.getThreshold() != null
                && c.getThreshold().compareTo(BigDecimal.ZERO) > 0;
        return hasThreshold
                ? "满 " + money(c.getThreshold()) + " 减 " + money(c.getDiscount())
                : "立减 " + money(c.getDiscount()) + "（无门槛）";
    }

    private static String toZhe(BigDecimal rate) {
        if (rate == null) return "折扣";
        return rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + "折";
    }

    private static String money(BigDecimal v) {
        if (v == null) return "¥0";
        return "¥" + v.stripTrailingZeros().toPlainString();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public String getRuleText() { return ruleText; }
    public void setRuleText(String ruleText) { this.ruleText = ruleText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }

    public String getClaimedAt() { return claimedAt; }
    public void setClaimedAt(String claimedAt) { this.claimedAt = claimedAt; }

    public String getUsedAt() { return usedAt; }
    public void setUsedAt(String usedAt) { this.usedAt = usedAt; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public boolean isUsable() { return usable; }
    public void setUsable(boolean usable) { this.usable = usable; }
}
