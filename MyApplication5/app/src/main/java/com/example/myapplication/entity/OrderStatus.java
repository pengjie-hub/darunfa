package com.example.myapplication.entity;

/**
 * 订单状态常量。
 * <p>
 * 职责边界：为 UI 层与数据访问层提供唯一的订单状态定义，
 * 避免 UI 直接依赖 {@code db.AppDatabase} 里的状态常量。
 * 字符串值必须与数据库表 {@code order_table.status} 的取值保持一致。
 */
public interface OrderStatus {

    String STATUS_PENDING_PAYMENT = "待付款";
    String STATUS_PENDING_SHIP = "待发货";
    String STATUS_PENDING_RECEIVE = "待收货";
    /**
     * 待评价。
     * <p>注意：后端 {@code backend.entity.OrderStatus} 中<b>不存在</b>「待评价」状态——
     * 待评价页展示的是状态为「已完成」的订单，因此该常量在真实数据下<b>不会命中</b>
     * （属 app 侧保留常量，勿据此判断真实分布）。</p>
     */
    String STATUS_PENDING_REVIEW = "待评价";
    String STATUS_DONE = "已完成";
    /**
     * 订单已取消。
     * <p>与后端 {@code backend.entity.OrderStatus.CANCELLED("已取消")} 对应，是真实存在的状态
     * （用户未付款前主动取消，或超时未支付）。</p>
     */
    String STATUS_CANCELLED = "已取消";
    /**
     * 已退货。
     * <p>注意：后端 {@code backend.entity.OrderStatus} 中<b>不存在</b>此状态，
     * 属 app 侧保留常量，真实数据下<b>不会命中</b>。</p>
     */
    String STATUS_RETURNED = "已退货";
    /** 用户已提交退款申请，等商家审核（此时钱未退）。 */
    String STATUS_REFUNDING = "退款中";
    /** 商家已同意退款，钱已退回。 */
    String STATUS_REFUNDED = "已退款";

    /**
     * 该状态的订单是否应出现在「退款/售后」入口里。
     * <p>
     * 唯一口径，由「我的」页角标（{@code MineFragment.refreshOrderBadges}）与
     * 列表页（{@code MineFunctionActivity.loadAllOrdersForReturn}）<b>共用</b>：
     * 以前两处各写一份过滤条件，角标数与点进去看到的卡片数会对不上。
     * </p>
     * <ul>
     *   <li>「已退款」「已退货」→ 钱/货已了结，不再展示；</li>
     *   <li>「退款中」→ <b>保留</b>，让用户看得到申请进度（列表页会隐藏申请按钮，防重复申请）。</li>
     * </ul>
     */
    static boolean isAfterSaleVisible(String status) {
        return !STATUS_RETURNED.equals(status) && !STATUS_REFUNDED.equals(status);
    }
}
