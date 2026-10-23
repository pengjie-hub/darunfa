package com.example.myapplication.remote.dto;

/**
 * 订单明细（后端 OrderItemDTO 对应）。
 */
public class OrderItemDTO {
    public long id;
    public long productId;
    public String productName;
    public String productImage;
    public double price;
    public int quantity;
    public double subtotal;

    /**
     * 这一件商品是否已被当前用户评价过。
     * <p>
     * 后端在「我的订单」类接口里逐件下发；后台订单列表等无用户视角的场景为 null，
     * 此时按「未评价」处理。待评价列表按它逐行过滤 —— 一单两件只评了一件时，
     * 评过的那行应立刻消失，另一行继续提示去评价。
     */
    public Boolean reviewed;
}
