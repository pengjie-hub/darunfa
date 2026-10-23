package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.RechargeRequest;
import com.example.myapplication.remote.dto.WalletDTO;

/**
 * 钱包数据访问的远程实现，对接后端 {@code /api/wallet}。
 * <p>
 * 回调语义保持与其它远程仓储一致：业务成功走 {@link ApiCallback#onSuccess}，
 * 业务错误（HTTP 200 + body code=400）与网络错误都走 {@link ApiCallback#onError}（由
 * {@link ApiUtils} 统一转换）。
 */
public class RemoteWalletRepository implements WalletRepository {

    @Override
    public void getWallet(Integer limit, final ApiCallback<WalletDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().wallet(limit), new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                cb.onSuccess(data);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void recharge(double amount, String requestId, final ApiCallback<WalletDTO> cb) {
        RechargeRequest req = new RechargeRequest(amount, requestId);
        ApiUtils.enqueue(RetrofitClient.api().recharge(req), new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                cb.onSuccess(data);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }
}
