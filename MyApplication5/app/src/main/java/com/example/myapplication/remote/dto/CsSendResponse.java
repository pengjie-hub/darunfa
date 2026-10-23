package com.example.myapplication.remote.dto;

/**
 * 客服发消息的响应：一次返回刚写入的两条消息（客户消息 + 自动回复），前端原地追加气泡即可。
 */
public class CsSendResponse {
    public long conversationId;
    public String conversationStatus;
    /** 刚发出去的那条客户消息。 */
    public CsMessageDTO userMessage;
    /** 服务端生成的回复（AI 自动应答，或转人工提示）。 */
    public CsMessageDTO reply;
}
