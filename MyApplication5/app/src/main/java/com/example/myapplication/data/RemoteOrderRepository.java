package com.example.myapplication.data;

import com.example.myapplication.entity.OrderBrief;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.entity.OrderStatus;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.DtoMapper;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CreateOrderRequest;
import com.example.myapplication.remote.dto.OrderDTO;
import com.example.myapplication.remote.dto.PayRequest;
import com.example.myapplication.remote.dto.RefundApplyRequest;
import com.example.myapplication.remote.dto.RefundDTO;

import java.util.List;

/**
 * 订单数据访问的远程实现。
 * <p>
 * 中文状态名与后端枚举的映射：
 * 待付款→PENDING_PAYMENT、待发货→PAID、待收货→SHIPPED、待评价/已完成→COMPLETED、已退货→CANCELLED。
 * <p>
 * ⚠️ 下单与支付是两步：{@link #createOrder} 只创建订单（后端置 PENDING_PAYMENT「待付款」），
 * 必须由收银台（PayActivity）经用户点击确认后调用 {@link #pay} 才进入「待发货」。
 * 历史上这里在下单成功后顺手把款付了，导致支付环节被整个跳过。
 */
public class RemoteOrderRepository implements OrderRepository {

    @Override
    public void createOrder(List<Long> cartItemIds, String receiverName, String receiverPhone,
                            String receiverAddress, Long userCouponId,
                            final ApiCallback<OrderBrief> cb) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.cartItemIds = cartItemIds;
        req.receiverName = receiverName;
        req.receiverPhone = receiverPhone;
        req.receiverAddress = receiverAddress;
        req.userCouponId = userCouponId;

        ApiUtils.enqueue(RetrofitClient.api().createOrder(req), new ApiCallback<OrderDTO>() {
            @Override
            public void onSuccess(OrderDTO order) {
                // 只下单，不支付：订单停在「待付款」，等收银台确认
                cb.onSuccess(toBrief(order));
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void pay(long orderId, String method, final ApiCallback<Boolean> cb) {
        // 把用户选中的支付方式带进 body；method 为 null 时后端按 WECHAT 兜底
        PayRequest req = new PayRequest(method);
        ApiUtils.enqueue(RetrofitClient.api().pay(orderId, req), new ApiCallback<OrderDTO>() {
            @Override
            public void onSuccess(OrderDTO data) {
                cb.onSuccess(Boolean.TRUE);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getOrderDetail(long orderId, final ApiCallback<OrderBrief> cb) {
        ApiUtils.enqueue(RetrofitClient.api().orderDetail(orderId), new ApiCallback<OrderDTO>() {
            @Override
            public void onSuccess(OrderDTO data) {
                cb.onSuccess(toBrief(data));
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    /** 后端 OrderDTO → 收银台要用的整单概要。 */
    private OrderBrief toBrief(OrderDTO o) {
        if (o == null) return new OrderBrief();
        return new OrderBrief(o.id, o.orderNo, o.payAmount, o.discountAmount,
                o.statusLabel == null ? "" : o.statusLabel);
    }

    @Override
    public void getAllOrders(final ApiCallback<List<OrderInfo>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().orders(null), new ApiCallback<List<OrderDTO>>() {
            @Override
            public void onSuccess(List<OrderDTO> data) {
                cb.onSuccess(DtoMapper.toOrderInfoList(data));
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void getOrdersByStatus(String statusLabel, final ApiCallback<List<OrderInfo>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().orders(toBackendStatus(statusLabel)),
                new ApiCallback<List<OrderDTO>>() {
                    @Override
                    public void onSuccess(List<OrderDTO> data) {
                        cb.onSuccess(DtoMapper.toOrderInfoList(data));
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void confirmReceive(long orderId, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().confirm(orderId), new ApiCallback<OrderDTO>() {
            @Override
            public void onSuccess(OrderDTO data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void cancelOrder(long orderId, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().cancel(orderId), new ApiCallback<OrderDTO>() {
            @Override
            public void onSuccess(OrderDTO data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void applyRefund(long orderId, String reason, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().applyRefund(orderId, new RefundApplyRequest(reason)),
                new ApiCallback<RefundDTO>() {
                    @Override
                    public void onSuccess(RefundDTO data) {
                        cb.onSuccess(true);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    /** 中文状态名 → 后端 OrderStatus 枚举名 */
    private String toBackendStatus(String label) {
        if (label == null || label.isEmpty()) return null;
        if (OrderStatus.STATUS_PENDING_SHIP.equals(label)) return "PAID";
        if (OrderStatus.STATUS_PENDING_RECEIVE.equals(label)) return "SHIPPED";
        if (OrderStatus.STATUS_PENDING_REVIEW.equals(label)) return "COMPLETED";
        if (OrderStatus.STATUS_DONE.equals(label)) return "COMPLETED";
        if (OrderStatus.STATUS_RETURNED.equals(label)) return "CANCELLED";
        if (OrderStatus.STATUS_REFUNDING.equals(label)) return "REFUNDING";
        if (OrderStatus.STATUS_REFUNDED.equals(label)) return "REFUNDED";
        return null;
    }
}
