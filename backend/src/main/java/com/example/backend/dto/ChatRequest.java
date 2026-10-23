package com.example.backend.dto;

/**
 * /api/chat 对话接口的请求体。
 * <p>
 * 客户端发送一条用户消息，后端据此调用 DeepSeek 并返回 AI 回复。
 */
public class ChatRequest {

    /** 用户输入的消息内容 */
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
