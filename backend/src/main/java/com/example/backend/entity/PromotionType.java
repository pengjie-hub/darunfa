package com.example.backend.entity;

/**
 * 促销类型枚举。
 */
public enum PromotionType {
    FULL_REDUCTION("满减"),   // 满 threshold 减 discount（按订单金额门槛）
    DISCOUNT("折扣");          // 折扣率 discount（0~1，如 0.9 表示 9 折）

    private final String label;

    PromotionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
