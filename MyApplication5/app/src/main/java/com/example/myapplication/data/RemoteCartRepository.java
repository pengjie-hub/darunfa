package com.example.myapplication.data;

import com.example.myapplication.entity.CarInfo;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.DtoMapper;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AddCartRequest;
import com.example.myapplication.remote.dto.CartItemDTO;
import com.example.myapplication.remote.dto.UpdateCartRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车数据访问的远程实现。
 */
public class RemoteCartRepository implements CartRepository {

    @Override
    public void addCar(int productId, int quantity, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().addCart(new AddCartRequest(productId, quantity)),
                new ApiCallback<CartItemDTO>() {
                    @Override
                    public void onSuccess(CartItemDTO data) {
                        cb.onSuccess(true);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void removeCar(int productId, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().removeCart(productId), new ApiCallback<Void>() {
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
    public void removeCars(List<Integer> productIds, final ApiCallback<Boolean> cb) {
        List<Long> longs = new ArrayList<>();
        if (productIds != null) {
            for (Integer id : productIds) longs.add((long) id);
        }
        ApiUtils.enqueue(RetrofitClient.api().removeCartBatch(longs), new ApiCallback<Void>() {
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
    public void updateProductQuantity(int productId, int quantity, final ApiCallback<Boolean> cb) {
        UpdateCartRequest req = new UpdateCartRequest();
        req.quantity = quantity;
        ApiUtils.enqueue(RetrofitClient.api().updateCart(productId, req), new ApiCallback<CartItemDTO>() {
            @Override
            public void onSuccess(CartItemDTO data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void updateProductSelected(int productId, boolean selected, final ApiCallback<Boolean> cb) {
        UpdateCartRequest req = new UpdateCartRequest();
        req.selected = selected;
        ApiUtils.enqueue(RetrofitClient.api().updateCart(productId, req), new ApiCallback<CartItemDTO>() {
            @Override
            public void onSuccess(CartItemDTO data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void updateAllProductsSelected(boolean selected, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().selectAll(selected), new ApiCallback<Void>() {
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
    public void getAllCars(final ApiCallback<List<CarInfo>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().cart(), new ApiCallback<List<CartItemDTO>>() {
            @Override
            public void onSuccess(List<CartItemDTO> data) {
                cb.onSuccess(DtoMapper.toCarInfoList(data));
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void clearCar(final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().clearCart(), new ApiCallback<Void>() {
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
}
