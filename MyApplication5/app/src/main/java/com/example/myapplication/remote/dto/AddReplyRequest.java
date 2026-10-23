package com.example.myapplication.remote.dto;

/**
 * 发表追评请求。
 */
public class AddReplyRequest {
    public String content;

    public AddReplyRequest(String content) {
        this.content = content;
    }
}
