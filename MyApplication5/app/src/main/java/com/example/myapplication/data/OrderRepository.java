package com.example.myapplication.data;

import com.example.myapplication.entity.OrderBrief;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.remote.ApiCallback;

import java.util.List;

/**
 * 订单数据访问接口（远程版，异步回调）。
 * <p>
 * 职责边界：只负责「订单」的读写——
 * 下单、支付、按状态查询订单、查询全部订单、确认收货、取消（退货）。
 * 订单状态取值见 {@link com.example.myapplication.entity.OrderStatus}，
 * 与后端枚举（待付款/待发货/待收货/已完成/已取消）的映射由实现层处理。
 * 不涉及商品、购物车、用户。
 * <p>
 * ⚠️ 下单与支付是**两步**：{@link #createOrder} 只创建订单（后端状态「待付款」），
 * 必须再经 {@link #pay} 才会进入「待发货」。不要在 createOrder 里顺手把款付掉，
 * 否则收银台（PayActivity）会被整个跳过。
 */
public interface OrderRepository {

    /**
     * 从购物车下单（cartItemIds 为购物车项 ID 列表）。
     * <p>
     * 成功后订单状态为「待付款」，回调带回 {@link OrderBrief}，由调用方唤起收银台。
     *
     * @param userCouponId 选用的优惠券 ID，可传 null（后端会校验归属/状态/门槛）
     */
    void createOrder(List<Long> cartItemIds, String receiverName, String receiverPhone,
                     String receiverAddress, Long userCouponId, ApiCallback<OrderBrief> cb);

    /**
     * 支付：待付款 → 待发货（后端同时写入支付流水）。
     *
     * @param method 支付方式，取值 {@code WECHAT | ALIPAY | BANK | CASH | BALANCE}
     *               （不传/为 null 时后端按 {@code WECHAT} 处理）。
     *               传 {@code BALANCE} 时由后端在同一事务里扣减账户余额并写余额流水——
     *               App 不做任何本地扣款；余额不足时后端返回业务错误（code=400）。
     */
    void pay(long orderId, String method, ApiCallback<Boolean> cb);

    /** 订单详情（收银台用：拿到订单号、应付金额与当前状态）。 */
    void getOrderDetail(long orderId, ApiCallback<OrderBrief> cb);

    void getAllOrders(ApiCallback<List<OrderInfo>> cb);

    /** statusLabel 为中文状态名（如“待发货”），传 null/空表示全部 */
    void getOrdersByStatus(String statusLabel, ApiCallback<List<OrderInfo>> cb);

    /** 确认收货（待收货 → 已完成） */
    void confirmReceive(long orderId, ApiCallback<Boolean> cb);

    /** 取消/退货（回补库存，仅待付款/待发货可取消） */
    void cancelOrder(long orderId, ApiCallback<Boolean> cb);

    /**
     * 申请退款（已付款订单：待发货 / 待收货 / 已完成）。
     * <p>
     * ⚠️ 只提交申请，订单进入「退款中」，<b>钱不会立刻退</b> ——
     * 必须等商家在后台同意后才会真正退回（回补库存、返还券、支付流水置已退款）。
     * 与 {@link #cancelOrder} 的区别：后者是用户自己取消未发货订单，立即生效。
     *
     * @param reason 退款原因，可为空
     */
    void applyRefund(long orderId, String reason, ApiCallback<Boolean> cb);
}
