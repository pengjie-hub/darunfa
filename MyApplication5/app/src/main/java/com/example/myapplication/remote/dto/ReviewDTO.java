package com.example.myapplication.remote.dto;

/**
 * 商品评论（后端 Review 实体对应）。
 */
public class ReviewDTO {
    public long id;
    public long productId;
    public long userId;
    public String userName;
    public double rating;
    public String content;
    public String createdAt;
}
