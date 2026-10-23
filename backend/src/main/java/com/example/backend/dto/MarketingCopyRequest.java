package com.example.backend.dto;

/**
 * 后台「AI 营销文案生成」请求体。
 *
 * <p>除 {@code name} 与 {@code extra} 至少要有一个外，其余字段全部可选。
 * 数值字段一律使用<b>包装类型</b>（{@link Double} / {@link Integer}），
 * 允许为 {@code null} 且避免拆箱 NPE。
 *
 * <ul>
 *   <li>{@code scene} — {@code PRODUCT}（商品，默认）| {@code PROMOTION}（活动）</li>
 *   <li>{@code channel} — {@code DETAIL}（商品详情页，默认）| {@code HOME_CARD}（首页/分类卡片）
 *       | {@code PUSH}（短信/推送）| {@code SOCIAL}（社群/朋友圈）</li>
 *   <li>{@code promoType} — {@code FULL_REDUCTION}（满减）| {@code DISCOUNT}（折扣），仅活动场景有意义</li>
 *   <li>{@code existing} 非空表示「润色/改写已有文案」场景</li>
 * </ul>
 */
public class MarketingCopyRequest {

    /** 场景：PRODUCT / PROMOTION，缺省 PRODUCT。 */
    private String scene;

    /** 渠道：DETAIL / HOME_CARD / PUSH / SOCIAL，缺省 DETAIL。 */
    private String channel;

    /** 商品/活动名称。 */
    private String name;

    /** 分类（如「水果」）。 */
    private String category;

    /** 现价（元），可为 null。 */
    private Double price;

    /** 原价（元），可为 null。 */
    private Double originalPrice;

    /** 计量单位（如「斤」「盒」）。 */
    private String unit;

    /** 关键词/卖点（空格或顿号分隔）。 */
    private String keywords;

    /** 目标人群。 */
    private String audience;

    /** 促销类型：FULL_REDUCTION / DISCOUNT（仅活动场景有意义）。 */
    private String promoType;

    /** 门槛金额（元），可为 null。 */
    private Double threshold;

    /**
     * 优惠数值，可为 null。
     * <p>满减场景为「减免金额（元）」，折扣场景为「折扣率」（如 0.9 表示 9 折）。
     * 因此这里使用 {@link Double} 而非 {@link Integer}：折扣率天然是小数，
     * 用 Double 可同时兼容整数与小数输入，且不影响任何整数载荷。
     */
    private Double discount;

    /** 已有文案（非空 = 润色/改写场景）。 */
    private String existing;

    /** 补充说明（关键词、规格、产地、限时、人群等自由文本）。 */
    private String extra;

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(Double originalPrice) { this.originalPrice = originalPrice; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getPromoType() { return promoType; }
    public void setPromoType(String promoType) { this.promoType = promoType; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public Double getDiscount() { return discount; }
    public void setDiscount(Double discount) { this.discount = discount; }

    public String getExisting() { return existing; }
    public void setExisting(String existing) { this.existing = existing; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
