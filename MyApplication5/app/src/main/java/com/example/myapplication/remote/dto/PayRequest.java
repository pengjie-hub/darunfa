package com.example.myapplication.remote.dto;

/**
 * 支付请求体：指定本次支付使用的支付方式。
 * <p>
 * 后端 {@code PUT /api/orders/{id}/pay} 新增了可选 body，{@code method} 取值：
 * {@code WECHAT | ALIPAY | BANK | CASH | BALANCE}。不传时后端按 {@code WECHAT} 处理（向后兼容）。
 * <p>
 * 注意：{@code BALANCE}（余额支付）由后端在同一个事务里扣减账户余额并写余额流水，
 * App 侧只负责把用户选择的支付方式带过去，不做任何本地扣款。
 */
public class PayRequest {

    /** 支付方式枚举名，见类注释。 */
    public String method;

    public PayRequest() {
    }

    public PayRequest(String method) {
        this.method = method;
    }
}
