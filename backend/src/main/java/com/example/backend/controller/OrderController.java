package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.CreateOrderRequest;
import com.example.backend.dto.OrderDTO;
import com.example.backend.dto.PayOrderRequest;
import com.example.backend.dto.RefundDTO;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.OrderService;
import com.example.backend.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单接口。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final RefundService refundService;

    public OrderController(OrderService orderService, RefundService refundService) {
        this.orderService = orderService;
        this.refundService = refundService;
    }

    @PostMapping
    public ApiResponse<OrderDTO> create(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                        @Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.create(userId, req));
    }

    @GetMapping
    public ApiResponse<List<OrderDTO>> list(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                            @RequestParam(required = false) OrderStatus status) {
        return ApiResponse.ok(orderService.list(userId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDTO> detail(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                        @PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(userId, id));
    }

    /**
     * 支付订单。请求体可选，{@code method} 指定支付方式（BALANCE 时从余额扣款）。
     * 缺省（含不传 body）向后兼容按微信支付处理。
     */
    @PutMapping("/{id}/pay")
    public ApiResponse<OrderDTO> pay(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) PayOrderRequest req) {
        return ApiResponse.ok(orderService.pay(userId, id, parseMethod(req)));
    }

    /** 解析支付方式：缺省（含不传 body）向后兼容为微信支付；非法值抛可读业务异常而非 Jackson 原始错误。 */
    private PaymentMethod parseMethod(PayOrderRequest req) {
        String raw = req == null ? null : req.getMethod();
        if (raw == null || raw.isBlank()) {
            return PaymentMethod.WECHAT;
        }
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的支付方式");
        }
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<OrderDTO> cancel(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                        @PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(userId, id));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<OrderDTO> confirm(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                         @PathVariable Long id) {
        return ApiResponse.ok(orderService.confirm(userId, id));
    }

    /**
     * 申请退款：只提交申请，订单进入「退款中」，等商家在后台审核。
     * <p>
     * 与 {@code /cancel} 的区别：cancel 是用户自己取消未发货订单（立即生效），
     * 这里是已付款订单申请退钱，必须经商家同意才会真正退回。
     */
    @PostMapping("/{id}/refund")
    public ApiResponse<RefundDTO> applyRefund(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                              @PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(refundService.apply(userId, id, reason));
    }

    /** 查看某订单的退款申请（无申请时 data 为 null）。 */
    @GetMapping("/{id}/refund")
    public ApiResponse<RefundDTO> refundOfOrder(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                                @PathVariable Long id) {
        return ApiResponse.ok(refundService.latestOfOrder(userId, id));
    }
}
