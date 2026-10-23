package com.example.myapplication.remote.dto;

/**
 * 购物车项（后端 CartItem 实体对应）。
 */
public class CartItemDTO {
    public long id;
    public long userId;
    public long productId;
    public String productName;
    public String productImage;
    public double price;
    public int quantity;
    public boolean selected;
    public String createdAt;
    public String updatedAt;
}
