package com.example.myapplication.remote.dto;

/**
 * 客服消息（后端 {@code CsMessage} 的 Map 序列化对应）。
 * <p>
 * {@code sender} 取值：{@code USER} 客户 / {@code AI} 智能客服 / {@code AGENT} 人工客服。
 * 前端据此决定气泡在左侧还是右侧。
 */
public class CsMessageDTO {
    public long id;
    public long conversationId;
    public String sender;
    public String senderName;
    public String content;
    public String createdAt;
    public boolean fromCustomer;

    /** 是否为我（客户）发出的消息 —— 右侧蓝色气泡。 */
    public boolean isMine() {
        return fromCustomer || "USER".equals(sender);
    }

    /** 是否人工客服发的（要打「人工」标，和 AI 自动回复区分开）。 */
    public boolean isAgent() {
        return "AGENT".equals(sender);
    }
}
