package com.example.myapplication.remote.dto;

/**
 * 发表评论请求。
 */
public class AddReviewRequest {
    public long productId;
    public double rating;
    public String content;
}
