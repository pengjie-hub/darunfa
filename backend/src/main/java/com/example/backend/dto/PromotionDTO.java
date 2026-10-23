package com.example.backend.dto;

import com.example.backend.entity.Promotion;
import com.example.backend.entity.PromotionType;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 前台促销活动展示 DTO。
 * <p>
 * 与 admin 端的 {@code Promotion} 原始实体不同，这个 DTO 面向"展示"：
 * <ul>
 *   <li>把枚举换成中文标签（{@code typeLabel}）；</li>
 *   <li>{@code tagText} 直接给出优惠角标文案（如「满 ¥100 减 ¥20」「9折」）；</li>
 *   <li>{@code description} 为空时按满减/折扣规则自动生成文案，前端无需再做计算；</li>
 *   <li>时间统一格式化为 {@code yyyy-MM-dd HH:mm} 字符串。</li>
 * </ul>
 */
public class PromotionDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    /** 标题（对应后台的"活动名称"）。 */
    private String name;
    /** 促销类型枚举名：FULL_REDUCTION / DISCOUNT。 */
    private String type;
    /** 促销类型中文：满减 / 折扣。 */
    private String typeLabel;
    private BigDecimal threshold;
    private BigDecimal discount;
    private Long productId;
    /** 活动配图 URL（可能为空，前端需做降级占位）。 */
    private String imageUrl;
    /** 活动描述（保证非空：原值为空时自动生成）。 */
    private String description;
    /** 跳转链接（可能为空，前端按 productId 兜底）。 */
    private String link;
    private String startTime;
    private String endTime;
    /** 优惠角标文案，如「满 ¥100 减 ¥20」「9折」「立减 ¥5」。 */
    private String tagText;

    public static PromotionDTO from(Promotion p) {
        PromotionDTO d = new PromotionDTO();
        d.id = p.getId();
        d.name = p.getName();
        d.type = p.getType() == null ? null : p.getType().name();
        d.typeLabel = p.getType() == null ? "" : p.getType().getLabel();
        d.threshold = p.getThreshold();
        d.discount = p.getDiscount();
        d.productId = p.getProductId();
        d.imageUrl = p.getImageUrl();
        d.link = p.getLink();
        d.description = buildDescription(p);
        d.startTime = p.getStartTime() == null ? null : p.getStartTime().format(FMT);
        d.endTime = p.getEndTime() == null ? null : p.getEndTime().format(FMT);
        d.tagText = buildTag(p);
        return d;
    }

    /** 描述为空时按促销规则生成一句人话。 */
    private static String buildDescription(Promotion p) {
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            return p.getDescription().trim();
        }
        if (p.getType() == PromotionType.DISCOUNT) {
            String zhe = toZhe(p.getDiscount());
            if (hasThreshold(p)) {
                return "全场商品享 " + zhe + " 优惠，单笔满 " + money(p.getThreshold()) + " 元可用";
            }
            return "全场商品享 " + zhe + " 优惠，无门槛立享";
        }
        // 满减
        if (hasThreshold(p)) {
            return "单笔消费满 " + money(p.getThreshold()) + " 元，立减 " + money(p.getDiscount()) + " 元";
        }
        return "单笔消费立减 " + money(p.getDiscount()) + " 元，无门槛立享";
    }

    /** 优惠角标文案。 */
    private static String buildTag(Promotion p) {
        if (p.getType() == PromotionType.DISCOUNT) {
            return toZhe(p.getDiscount());
        }
        if (hasThreshold(p)) {
            return "满 " + money(p.getThreshold()) + " 减 " + money(p.getDiscount());
        }
        return "立减 " + money(p.getDiscount());
    }

    /** 折扣率(0~1) → 「9折」「8.5折」 */
    private static String toZhe(BigDecimal rate) {
        if (rate == null) return "折扣";
        BigDecimal zhe = rate.multiply(BigDecimal.TEN).stripTrailingZeros();
        return zhe.toPlainString() + "折";
    }

    private static boolean hasThreshold(Promotion p) {
        return p.getThreshold() != null && p.getThreshold().compareTo(BigDecimal.ZERO) > 0;
    }

    /** 金额：去尾零 + 加 ¥ 前缀。 */
    private static String money(BigDecimal v) {
        if (v == null) return "¥0";
        return "¥" + v.stripTrailingZeros().toPlainString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getTagText() { return tagText; }
    public void setTagText(String tagText) { this.tagText = tagText; }
}
