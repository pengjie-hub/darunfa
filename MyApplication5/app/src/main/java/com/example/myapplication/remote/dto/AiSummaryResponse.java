package com.example.myapplication.remote.dto;

/**
 * AI 评论总结响应体（对应后端 POST /api/ai/summary 的 data 字段）。
 */
public class AiSummaryResponse {

    /** AI 生成的总结文本 */
    public String summary;
}
