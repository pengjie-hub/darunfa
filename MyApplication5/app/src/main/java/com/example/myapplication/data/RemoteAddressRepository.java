package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.AddressRequest;

import java.util.List;

/**
 * 收货地址数据访问的远程实现。
 */
public class RemoteAddressRepository implements AddressRepository {

    @Override
    public void list(final ApiCallback<List<AddressDTO>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().addresses(), cb);
    }

    @Override
    public void getDefault(final ApiCallback<AddressDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().defaultAddress(), cb);
    }

    @Override
    public void create(AddressRequest req, final ApiCallback<AddressDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().createAddress(req), cb);
    }

    @Override
    public void update(long id, AddressRequest req, final ApiCallback<AddressDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().updateAddress(id, req), cb);
    }

    @Override
    public void delete(long id, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().deleteAddress(id), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void setDefault(long id, final ApiCallback<AddressDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().setDefaultAddress(id), cb);
    }
}
