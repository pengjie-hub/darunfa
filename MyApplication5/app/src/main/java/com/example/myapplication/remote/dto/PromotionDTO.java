package com.example.myapplication.remote.dto;

/**
 * 前台促销活动展示 DTO（对应后端 {@code PromotionDTO}）。
 * <p>
 * 后端已把优惠规则转成可读文案，前端直接渲染：
 * <ul>
 *   <li>{@code name} —— 标题；</li>
 *   <li>{@code description} —— 描述（后端保证非空，为空时按规则生成）；</li>
 *   <li>{@code tagText} —— 优惠角标，如「满 ¥100 减 ¥20」「9折」；</li>
 *   <li>{@code imageUrl} —— 配图（可能为空，前端需降级为渐变占位）；</li>
 *   <li>{@code link} —— 跳转目标（可能为空，前端按 productId 兜底）。</li>
 * </ul>
 */
public class PromotionDTO {

    public long id;
    public String name;
    /** FULL_REDUCTION / DISCOUNT。 */
    public String type;
    /** 满减 / 折扣。 */
    public String typeLabel;
    public double threshold;
    public double discount;
    public Long productId;
    public String imageUrl;
    public String description;
    public String link;
    public String startTime;
    public String endTime;
    public String tagText;
}
