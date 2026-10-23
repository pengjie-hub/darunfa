package com.example.myapplication.remote.dto;

/**
 * 更新购物车项请求（数量/勾选状态，均可选）。
 */
public class UpdateCartRequest {
    public Integer quantity;
    public Boolean selected;
}
