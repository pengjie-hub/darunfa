package com.example.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台仪表盘统计 DTO。
 */
public class DashboardDTO {

    private long userCount;
    private long productCount;
    private long orderCount;
    private long pendingShipCount;       // 待发货订单数
    private long pendingPaymentCount;    // 待付款订单数
    private long shippedCount;           // 待收货订单数
    private long completedCount;         // 已完成订单数
    private long reviewCount;            // 评论总数
    private long lowStockCount;          // 低库存商品数(库存<=10)
    private BigDecimal totalSales;        // 累计销售额（已付款及以上）

    /** 超时未发货订单数：付款后超过 {@code AdminService.SLA_SHIP_DAYS} 天仍处于「待发货」。 */
    private long overdueShipCount;
    /** 上述超时订单的金额合计。 */
    private BigDecimal overdueShipAmount = BigDecimal.ZERO;
    /** 超时订单中最久的已等待天数（无超时订单时为 0）。 */
    private long oldestOverdueDays;
    /** 超时订单明细（最久的在前，最多若干条，仅供仪表盘提示，完整列表在订单管理页）。 */
    private List<OverdueShipment> overdueShips = new ArrayList<>();

    public long getUserCount() { return userCount; }
    public void setUserCount(long userCount) { this.userCount = userCount; }

    public long getProductCount() { return productCount; }
    public void setProductCount(long productCount) { this.productCount = productCount; }

    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }

    public long getPendingShipCount() { return pendingShipCount; }
    public void setPendingShipCount(long pendingShipCount) { this.pendingShipCount = pendingShipCount; }

    public long getPendingPaymentCount() { return pendingPaymentCount; }
    public void setPendingPaymentCount(long pendingPaymentCount) { this.pendingPaymentCount = pendingPaymentCount; }

    public long getShippedCount() { return shippedCount; }
    public void setShippedCount(long shippedCount) { this.shippedCount = shippedCount; }

    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

    public long getReviewCount() { return reviewCount; }
    public void setReviewCount(long reviewCount) { this.reviewCount = reviewCount; }

    public long getLowStockCount() { return lowStockCount; }
    public void setLowStockCount(long lowStockCount) { this.lowStockCount = lowStockCount; }

    public BigDecimal getTotalSales() { return totalSales; }
    public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }

    public long getOverdueShipCount() { return overdueShipCount; }
    public void setOverdueShipCount(long overdueShipCount) { this.overdueShipCount = overdueShipCount; }

    public BigDecimal getOverdueShipAmount() { return overdueShipAmount; }
    public void setOverdueShipAmount(BigDecimal overdueShipAmount) { this.overdueShipAmount = overdueShipAmount; }

    public long getOldestOverdueDays() { return oldestOverdueDays; }
    public void setOldestOverdueDays(long oldestOverdueDays) { this.oldestOverdueDays = oldestOverdueDays; }

    public List<OverdueShipment> getOverdueShips() { return overdueShips; }
    public void setOverdueShips(List<OverdueShipment> overdueShips) { this.overdueShips = overdueShips; }

    /** 超时未发货订单明细。 */
    public static class OverdueShipment {
        private String orderNo;
        private String userName;
        private BigDecimal payAmount;
        private long waitingDays;      // 已等待天数（自付款时间起算）
        private LocalDateTime paidAt;  // 起算时间（付款时间；缺失时退化为下单时间）

        public OverdueShipment() {}

        public OverdueShipment(String orderNo, String userName, BigDecimal payAmount,
                               long waitingDays, LocalDateTime paidAt) {
            this.orderNo = orderNo;
            this.userName = userName;
            this.payAmount = payAmount;
            this.waitingDays = waitingDays;
            this.paidAt = paidAt;
        }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public BigDecimal getPayAmount() { return payAmount; }
        public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

        public long getWaitingDays() { return waitingDays; }
        public void setWaitingDays(long waitingDays) { this.waitingDays = waitingDays; }

        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    }
}
