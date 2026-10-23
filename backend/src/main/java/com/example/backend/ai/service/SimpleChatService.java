package com.example.backend.ai.service;

import com.example.backend.ai.assistant.SimpleAssistant;
import org.springframework.stereotype.Service;

/**
 * 通用 AI 对话服务（演示端点用）。
 * <p>改造前它手写了「拼 messages → 调 DeepSeekClient.chat → 取 content」三步；
 * 现在只剩参数校验与一次委托，其余全部由 {@link SimpleAssistant} 声明式完成。</p>
 */
@Service
public class SimpleChatService {

    private final SimpleAssistant assistant;

    public SimpleChatService(SimpleAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * 根据用户消息生成 AI 回复。
     *
     * @param userMessage 用户输入
     * @return AI 回复文本
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return assistant.chat(userMessage.trim());
    }
}
