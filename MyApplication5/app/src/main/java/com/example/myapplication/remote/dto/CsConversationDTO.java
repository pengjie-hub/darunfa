package com.example.myapplication.remote.dto;

/**
 * 客服会话（后端 {@code CsConversation} 的 Map 序列化对应）。
 */
public class CsConversationDTO {
    public long id;
    public long userId;
    /** 客户显示名。 */
    public String userName;
    /** AI / HUMAN / CLOSED。 */
    public String status;
    /** 中文状态名：AI 应答 / 人工跟进 / 已结束。 */
    public String statusLabel;
    public String lastMessage;
    public String lastMessageAt;
    public String createdAt;
    public int messageCount;
    /** 仅后台接口返回：该会话的未读条数。 */
    public Integer unread;

    public boolean isHumanHandling() {
        return "HUMAN".equals(status);
    }
}
