package com.example.backend.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 大润发 AI 助手（前台用户向）响应。
 * <p>
 * 结构：
 * <ul>
 *   <li>{@code reply} —— 最终给用户阅读的自然语言回复</li>
 *   <li>{@code attachments} —— 工具调用留下的结构化数据（商品列表 / 参数对比 / 物流时间线 / 订单预览），
 *       前端按 {@code type} 渲染为对应卡片</li>
 * </ul>
 * <p>
 * 当 LLM 只做了"打招呼 / 闲聊"而没有调用任何工具时，{@code attachments} 可为空。
 */
public class AssistantChatResponse {

    private String reply;
    private List<ChatAttachmentDTO> attachments;

    public AssistantChatResponse() {}

    public AssistantChatResponse(String reply, List<ChatAttachmentDTO> attachments) {
        this.reply = reply;
        this.attachments = attachments;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<ChatAttachmentDTO> getAttachments() { return attachments; }
    public void setAttachments(List<ChatAttachmentDTO> attachments) { this.attachments = attachments; }

    /**
     * 单一附件：type + 原始 JSON data。前端凭 type 选择卡片布局，data 内容随 type 不同而不同。
     */
    public static class ChatAttachmentDTO {
        private String type;
        private JsonNode data;

        public ChatAttachmentDTO() {}

        public ChatAttachmentDTO(String type, JsonNode data) {
            this.type = type;
            this.data = data;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public JsonNode getData() { return data; }
        public void setData(JsonNode data) { this.data = data; }
    }
}
