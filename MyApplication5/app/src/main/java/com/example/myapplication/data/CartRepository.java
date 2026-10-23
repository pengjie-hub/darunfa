package com.example.myapplication.data;

import com.example.myapplication.entity.CarInfo;
import com.example.myapplication.remote.ApiCallback;

import java.util.List;

/**
 * 购物车数据访问接口（远程版，异步回调）。
 * <p>
 * 职责边界：只负责「购物车」的读写——
 * 加入/移除购物车、批量移除、修改数量/勾选状态、全选/反选、查询购物车、清空购物车。
 * 总价/总数/已选等派生数据由 UI 基于查询结果本地计算。
 * 收货人信息归订单域管理，购物车不再单独维护联系方式。
 */
public interface CartRepository {

    void addCar(int productId, int quantity, ApiCallback<Boolean> cb);

    void removeCar(int productId, ApiCallback<Boolean> cb);

    void removeCars(List<Integer> productIds, ApiCallback<Boolean> cb);

    void updateProductQuantity(int productId, int quantity, ApiCallback<Boolean> cb);

    void updateProductSelected(int productId, boolean selected, ApiCallback<Boolean> cb);

    void updateAllProductsSelected(boolean selected, ApiCallback<Boolean> cb);

    void getAllCars(ApiCallback<List<CarInfo>> cb);

    void clearCar(ApiCallback<Boolean> cb);
}
