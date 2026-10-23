package com.example.backend.dto;

/**
 * 客服发消息请求体（前台客户发消息与后台客服回复共用）。
 */
public class CsMessageRequest {

    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
