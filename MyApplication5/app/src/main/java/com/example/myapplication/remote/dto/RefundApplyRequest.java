package com.example.myapplication.remote.dto;

/**
 * 申请退款请求体（后端 {@code POST /api/orders/{id}/refund}）。
 */
public class RefundApplyRequest {

    /** 退款原因，可为空（后端会用默认文案兜底）。 */
    public String reason;

    public RefundApplyRequest() {
    }

    public RefundApplyRequest(String reason) {
        this.reason = reason;
    }
}
