package com.example.myapplication.remote.dto;

import java.util.List;

/**
 * AI 评论总结请求体（对应后端 POST /api/ai/summary）。
 */
public class AiSummaryRequest {

    /** 商品名称（可选，用于生成更具体的总结） */
    public String productName;

    /** 评论文本列表 */
    public List<String> reviews;

    public AiSummaryRequest(String productName, List<String> reviews) {
        this.productName = productName;
        this.reviews = reviews;
    }
}
