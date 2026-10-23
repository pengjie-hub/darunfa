package com.example.backend.ai.config;

import com.example.backend.ai.memory.JpaBackedChatMemory;
import com.example.backend.ai.memory.JpaChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配（记忆层）。
 *
 * <h3>数据流</h3>
 * <pre>
 *   @AiService 方法调用
 *        ↓ 携带 @MemoryId（"u:1" 登录用户 / "anon:xxx" 匿名）
 *   ChatMemoryProvider（本类）→ 每次调用现造一个 JpaBackedChatMemory
 *        ↓ 载入历史 / 追加落库
 *   JpaChatMemoryStore（ai_chat_messages 表，DB 里只留 user / assistant 的对话正文）
 * </pre>
 *
 * <h3>为什么返回 JpaBackedChatMemory 而不是 MessageWindowChatMemory</h3>
 * 框架自带的窗口实现会在每次 {@code add()} 时把 {@code ChatMemoryStore} 整个重读一遍，
 * 而我们的 store 是纯文本表，装不下系统提示、{@code tool_calls} 与工具结果 ——
 * 回读时它们会被丢掉，工具调用因此必然失败。
 * {@link JpaBackedChatMemory} 改为「载入一次、请求内驻留、尾部追加落库」，
 * 详细原因与后果见该类注释。
 *
 * <p>窗口大小由 {@code ai.memory.max-messages} 控制，
 * 它决定<b>每次注入模型多少条历史</b>；数据库始终保留完整会话，供 {@code /history} 接口回放。
 */
@Configuration
@EnableConfigurationProperties(AiMemoryProperties.class)
public class AiMemoryConfig {

    /** ChatMemoryProvider Bean 名，供 {@code @AiService(chatMemoryProvider = ...)} 引用。 */
    public static final String MEMORY_PROVIDER = "chatMemoryProvider";

    /**
     * 会话记忆工厂。
     * <p>
     * 刻意<b>每次调用都新建实例</b>：{@link JpaBackedChatMemory} 是有状态的
     * （它缓存了本次请求的消息列表），复用会让不同请求、不同用户的上下文互相污染。
     * 每次新建的成本只是从数据库读一次最近若干条，可以忽略。
     */
    @Bean(MEMORY_PROVIDER)
    public ChatMemoryProvider chatMemoryProvider(JpaChatMemoryStore store, AiMemoryProperties props) {
        return memoryId -> new JpaBackedChatMemory(memoryId, props.getMaxMessages(), store);
    }
}
