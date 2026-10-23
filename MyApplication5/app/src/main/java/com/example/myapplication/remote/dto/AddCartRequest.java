package com.example.myapplication.remote.dto;

/**
 * 加入购物车请求。
 */
public class AddCartRequest {
    public long productId;
    public int quantity;

    public AddCartRequest(long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
