package com.example.myapplication.remote.dto;

/**
 * 商品信息（后端 Product 实体对应）。
 */
public class ProductDTO {
    public long id;
    public long categoryId;
    public String name;
    public String subtitle;
    public String description;
    public double price;
    public double originalPrice;
    public int stock;
    public int sales;
    public String unit;
    public String imageUrl;
    public String images;
    public int status;
    public String createdAt;
    public String updatedAt;
}
