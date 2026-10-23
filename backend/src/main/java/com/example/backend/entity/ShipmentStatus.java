package com.example.backend.entity;

/**
 * 物流状态枚举。
 */
public enum ShipmentStatus {
    SHIPPED("已发货"),
    IN_TRANSIT("运输中"),
    DELIVERED("已送达");

    private final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
