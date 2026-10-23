package com.example.myapplication.remote.dto;

/**
 * 单条余额流水（对应后端 {@code BalanceTransactionDTO}）。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code type}：{@code INIT / RECHARGE / PAY / REFUND}；</li>
 *   <li>{@code typeLabel}：后端给好的中文标签（初始赠送 / 充值 / 余额支付 / 退款返现），UI 直接展示；</li>
 *   <li>{@code amount}：<b>有符号</b>金额，正=入账、负=出账（UI 需按符号补 +/- 前缀）；</li>
 *   <li>{@code balanceAfter}：本次变动后的余额快照。</li>
 * </ul>
 */
public class BalanceTransactionDTO {

    public long id;
    public String type;
    public String typeLabel;
    /** 有符号金额：正=入账、负=出账。 */
    public double amount;
    /** 变动后余额。 */
    public double balanceAfter;
    public long orderId;
    public String remark;
    public String createdAt;
}
