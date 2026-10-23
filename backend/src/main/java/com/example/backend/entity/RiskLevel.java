package com.example.backend.entity;

/**
 * 风险等级枚举，用于订单风险评分与列表筛选。
 *
 * <ul>
 *   <li>{@link #LOW}    — 低风险，可直接放行</li>
 *   <li>{@link #MEDIUM} — 中风险，建议人工复核</li>
 *   <li>{@link #HIGH}   — 高风险，建议暂停发货 / 人工审核</li>
 * </ul>
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}