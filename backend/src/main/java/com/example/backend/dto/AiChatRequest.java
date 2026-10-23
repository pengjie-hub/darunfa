package com.example.backend.dto;

/** AI 运营助手提问请求体。 */
public class AiChatRequest {

    /** 用户提问内容，1-500 字。 */
    private String message;

    public AiChatRequest() {}

    public AiChatRequest(String message) { this.message = message; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
