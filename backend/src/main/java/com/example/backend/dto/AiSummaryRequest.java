package com.example.backend.dto;

import java.util.List;

/**
 * AI 评论总结请求体。
 * <p>
 * 由 Android 前端发起，携带商品名称与该商品的评论文本列表，
 * 后端据此拼接 Prompt 并调用 DeepSeek 生成总结。
 */
public class AiSummaryRequest {

    /** 商品名称（可选，用于生成更具体的总结） */
    private String productName;

    /** 评论文本列表 */
    private List<String> reviews;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<String> getReviews() {
        return reviews;
    }

    public void setReviews(List<String> reviews) {
        this.reviews = reviews;
    }
}
