package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 通用对话助手（演示端点 {@code POST /api/chat} 使用）。
 * <p>
 * 保留它的意义在于：这是「最小可用的 LangChain4j 接入样例」——
 * 没有记忆、没有工具、没有检索，只有一句提示词。新增一个 AI 能力时，
 * 从这个文件开始照抄即可，不必翻其他复杂助手。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.CHATTY_MODEL
)
public interface SimpleAssistant {

    @SystemMessage(AiPrompts.SIMPLE_CHAT)
    String chat(@UserMessage String message);
}
