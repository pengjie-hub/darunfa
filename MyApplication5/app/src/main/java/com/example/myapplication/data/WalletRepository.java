package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.dto.WalletDTO;

/**
 * 钱包数据访问接口（远程版，异步回调）。
 * <p>
 * 职责边界：只负责「账户余额」相关读写——查询余额/流水、充值。
 * 余额支付（{@link OrderRepository#pay}）虽然会扣余额，但属于<b>订单</b>的支付动作，
 * 因此留在 {@link OrderRepository}，本接口不重复提供。
 */
public interface WalletRepository {

    /**
     * 查询钱包（余额 + 最近流水）。
     *
     * @param limit 流水条数上限；传 {@code null} 时由后端取默认值（20）
     */
    void getWallet(Integer limit, ApiCallback<WalletDTO> cb);

    /**
     * 充值。
     *
     * @param amount    充值金额（元），后端校验 &gt; 0、≤ 50000、最多 2 位小数
     * @param requestId 客户端幂等键（同一 id 重放不会重复入账）
     */
    void recharge(double amount, String requestId, ApiCallback<WalletDTO> cb);
}
