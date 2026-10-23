package com.example.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 大润发 AI 助手请求体。
 * <p>
 * 字段：
 * <ul>
 *   <li>{@code message} —— 当前用户输入的消息（必填，1-1000 字）</li>
 *   <li>{@code history} —— 上下文历史消息（可选）。
 *       每条为 {@code {role: "user"|"assistant", content: "..."}}，
 *       role 为 OpenAI 协议标准值。本服务把 history 与当前 message 一起提交给 LLM。</li>
 * </ul>
 * <p>
 * <b>前端兼容</b>：当前端组件未发 history（老版本）时，{@code history} 为 null 或空数组，
 * AssistantChatService 会自动忽略并单轮回复。
 */
public class AssistantChatRequest {

    private String message;
    private List<HistoryItem> history;

    /**
     * 是否为本会话的第一条消息（由前端根据消息列表长度判定）。
     * <p>
     * 用途：提示词里的【首次回复要求】（日期 + 星期问候）只在首条消息时生效。
     * <p>
     * 为什么必须由前端提供、而不是后端用"是否有历史"判断：
     * 本服务会从数据库加载<b>跨会话</b>的持久记忆历史，因此即便进入新会话，
     * 后端也总能查到上一轮会话的记录，无法区分"新会话首句"与"会话中途"。
     */
    private Boolean sessionFirst;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<HistoryItem> getHistory() { return history; }
    public void setHistory(List<HistoryItem> history) { this.history = history; }

    public Boolean getSessionFirst() { return sessionFirst; }
    public void setSessionFirst(Boolean sessionFirst) { this.sessionFirst = sessionFirst; }

    /** 是否为会话首条消息；前端未传（老版本）时保守视为 false（不触发开场问候）。 */
    public boolean isSessionFirst() {
        return Boolean.TRUE.equals(sessionFirst);
    }

    public static class HistoryItem {
        private String role;
        private String content;

        public HistoryItem() {}
        public HistoryItem(String role, String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /** 防御性地获取 history（null-safe 返回空列表，避免 NPE）。 */
    public List<HistoryItem> safeHistory() {
        return history == null ? new ArrayList<>() : history;
    }
}
