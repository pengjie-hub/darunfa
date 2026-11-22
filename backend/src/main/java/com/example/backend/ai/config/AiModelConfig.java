package com.example.backend.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ChatModel 装配（模型层）。
 *
 * <h3>为什么不用 open-ai starter 的属性自动装配</h3>
 * <ol>
 *   <li>本系统需要<b>两个温度档</b>的模型（严谨档 0.3 / 口语档 0.6），自动装配只会产生一个 Bean；</li>
 *   <li>模型是「基础设施」，显式声明便于在架构图里一眼看清依赖方向，也便于按 Bean 名做
 *       {@code @AiService(wiringMode = EXPLICIT, chatModel = "...")} 的精确装配；</li>
 *   <li>避免与第三方 starter 的条件化自动装配产生 Bean 数量歧义。</li>
 * </ol>
 *
 * <h3>全系统只有一个模型名</h3>
 * 两个 Bean <b>除温度外完全相同</b>，{@code modelName} 都取自 {@link DeepSeekProperties#getModel()}
 * （即 {@code deepseek.model}）。全站 6 个 {@code @AiService}（小 Demo / 小运 / 小发 / 在线客服 /
 * 评论分析 / 风控评分）只通过这两个 Bean 拿模型，没有任何助手单独指定模型 —— 因此<b>改
 * {@code deepseek.model} 一处即改全站</b>。
 * <p>
 * 装配完成时会打印一行 {@code [AI] 模型装配} 日志（含实际生效的 model 名）。模型目录会随
 * 平台更新而变化（历史踩坑：早期 {@code GET /v1/models} 返回的是 {@code deepseek-v4-flash}，
 * 后来变成 {@code deepseek-flash}），所以每次启动都留一条可核对的证据，避免「配置写了但没生效」。
 *
 * <h3>接入 DeepSeek 的方式</h3>
 * DeepSeek 提供 OpenAI 兼容的 {@code /v1/chat/completions}，因此直接复用 LangChain4j 的
 * {@link OpenAiChatModel}，只替换 {@code baseUrl / apiKey / modelName} 三项即可，
 * 后续要换成 OpenAI、通义、Kimi 等兼容服务，也只改配置不动代码。
 */
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class AiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);

    /** 严谨档模型 Bean 名，供 {@code @AiService(chatModel = ...)} 引用。 */
    public static final String STRICT_MODEL = "aiChatModel";

    /** 口语档模型 Bean 名。 */
    public static final String CHATTY_MODEL = "aiChattyChatModel";

    /**
     * 严谨档：温度 {@code deepseek.api.temperature}（默认 0.3）。
     * <p>用于小运运营问答、评论情感分析、评论总结、订单风控评分 —— 这些场景要么要求输出可被程序解析
     * （JSON/枚举），要么要求数字口径稳定，低温度能显著降低发散与格式跑偏。
     */
    @Bean(STRICT_MODEL)
    public ChatModel aiChatModel(DeepSeekProperties props) {
        return build(props, props.getTemperature(), STRICT_MODEL, "严谨档");
    }

    /**
     * 口语档：温度 {@code deepseek.api.chatty-temperature}（默认 0.6）。
     * <p>用于「小发」购物助手与 AI 在线客服 —— 需要自然、亲切、有变化的表达，过高温度会开始胡编商品/物流。
     */
    @Bean(CHATTY_MODEL)
    public ChatModel aiChattyChatModel(DeepSeekProperties props) {
        return build(props, props.getChattyTemperature(), CHATTY_MODEL, "口语档");
    }

    /** 统一的模型构建逻辑：两个 Bean 除温度外完全一致，保证行为可预期。 */
    private static ChatModel build(DeepSeekProperties props, Double temperature, String beanName, String tier) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "DeepSeek API Key 未配置：请设置环境变量 DEEPSEEK_API_KEY，"
                            + "或在 application.properties 中显式配置 deepseek.api-key");
        }
        int timeoutSeconds = props.getTimeoutSeconds() == null ? 60 : props.getTimeoutSeconds();
        int maxRetries = props.getMaxRetries() == null ? 1 : props.getMaxRetries();
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(props.getModel())
                .temperature(temperature)
                .maxTokens(props.getMaxTokens())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(props.isLogRequests())
                .logResponses(props.isLogResponses())
                .build();
        log.info("[AI] 模型装配：bean={} tier={} model={} temperature={} baseUrl={} maxTokens={} timeout={}s retries={}",
                beanName, tier, props.getModel(), temperature, props.getBaseUrl(),
                props.getMaxTokens(), timeoutSeconds, maxRetries);
        return model;
    }
}
