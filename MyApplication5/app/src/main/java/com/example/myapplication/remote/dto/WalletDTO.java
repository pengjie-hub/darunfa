package com.example.myapplication.remote.dto;

import java.util.List;

/**
 * 钱包快照（对应后端 {@code WalletDTO}）：当前余额 + 最近的余额流水。
 * <p>
 * {@code GET /api/wallet} 与 {@code POST /api/wallet/recharge} 都返回本结构
 * （充值成功 / 幂等重放 同样返回完整钱包），因此充值成功后可以直接用返回值刷新余额与明细。
 */
public class WalletDTO {

    /** 当前账户余额（元）。 */
    public double balance;
    /** 余额流水（后端已按时间倒序）。可能为 {@code null}。 */
    public List<BalanceTransactionDTO> transactions;
}
