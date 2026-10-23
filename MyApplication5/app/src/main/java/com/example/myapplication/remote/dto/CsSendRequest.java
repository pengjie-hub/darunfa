package com.example.myapplication.remote.dto;

/**
 * 客服发消息请求体（前台客户发送 / 后台客服回复共用）。
 */
public class CsSendRequest {
    public String content;

    public CsSendRequest(String content) {
        this.content = content;
    }
}
