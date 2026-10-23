package com.example.backend.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘可视化数据：近 7 天销售趋势 + 订单状态分布 + 分类商品分布 + 支付方式分布。
 */
public class DashboardChartsDTO {

    /** 近 7 天趋势（含今天，共 7 个点）。 */
    private List<TrendPoint> salesTrend = new ArrayList<>();

    /** 订单状态分布。 */
    private List<DistPoint> orderStatusDist = new ArrayList<>();

    /** 分类商品数量分布（取前 N 个，其余归入「其他」）。 */
    private List<DistPoint> categoryDist = new ArrayList<>();

    /** 支付方式分布。 */
    private List<DistPoint> paymentDist = new ArrayList<>();

    /** 趋势点：单日订单数与销售额。 */
    public static class TrendPoint {
        private String date;          // 如 09-07
        private long orderCount;      // 当日下单数（全部状态）
        private BigDecimal salesAmount; // 当日有效销售额（已付款及以上）

        public TrendPoint() {}

        public TrendPoint(String date, long orderCount, BigDecimal salesAmount) {
            this.date = date;
            this.orderCount = orderCount;
            this.salesAmount = salesAmount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public long getOrderCount() { return orderCount; }
        public void setOrderCount(long orderCount) { this.orderCount = orderCount; }

        public BigDecimal getSalesAmount() { return salesAmount; }
        public void setSalesAmount(BigDecimal salesAmount) { this.salesAmount = salesAmount; }
    }

    /** 分布点：名称与数值。 */
    public static class DistPoint {
        private String name;
        private long value;

        public DistPoint() {}

        public DistPoint(String name, long value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
    }

    public List<TrendPoint> getSalesTrend() { return salesTrend; }
    public void setSalesTrend(List<TrendPoint> salesTrend) { this.salesTrend = salesTrend; }

    public List<DistPoint> getOrderStatusDist() { return orderStatusDist; }
    public void setOrderStatusDist(List<DistPoint> orderStatusDist) { this.orderStatusDist = orderStatusDist; }

    public List<DistPoint> getCategoryDist() { return categoryDist; }
    public void setCategoryDist(List<DistPoint> categoryDist) { this.categoryDist = categoryDist; }

    public List<DistPoint> getPaymentDist() { return paymentDist; }
    public void setPaymentDist(List<DistPoint> paymentDist) { this.paymentDist = paymentDist; }
}
