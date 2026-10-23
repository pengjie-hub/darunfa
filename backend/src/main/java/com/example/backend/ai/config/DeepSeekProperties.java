package com.example.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek（OpenAI 兼容协议）模型接入参数。
 * <p>
 * 绑定 {@code application.properties} 的 {@code deepseek.*}，是 AI 模型配置的<b>唯一来源</b>：
 * <ul>
 *   <li>{@code base-url} —— 兼容端点根地址，例如 {@code https://api.deepseek.com/v1}。
 *       LangChain4j 会在其后拼接 {@code /chat/completions}，因此<b>不要</b>把路径写进 base-url。</li>
 *   <li>{@code temperature} / {@code chatty-temperature} —— 两档采样温度，见 {@link AiModelConfig}。</li>
 *   <li>{@code timeout-seconds} / {@code max-retries} —— 网络层容错（原手写客户端是写死的 60s 超时、无重试）。</li>
 * </ul>
 * <p>
 * <b>命名注意</b>：{@code @ConfigurationProperties} 的松绑定是按「属性名去掉前缀后的剩余段」匹配
 * accessor 的，因此 {@code deepseek.api-key} 对应字段 {@code apiKey}（属性名 {@code key} 是匹配不上的）。
 * 环境变量 {@code DEEPSEEK_API_KEY} / {@code DEEPSEEK_BASE_URL} / {@code DEEPSEEK_MODEL}
 * 由 Spring Boot 自动映射并覆盖同名配置，无需在此写 {@code ${ENV:default}} 占位符。
 */
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** 兼容端点根地址（不含 /chat/completions）。 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key。为空时启动即失败（fail-fast），避免运行期才暴露配置缺失。 */
    private String apiKey;

    /** 模型名，取值见 {@code GET {base-url}/models}，例如 deepseek-flash（DeepSeek-V4.1-Flash）。 */
    private String model = "deepseek-flash";

    /** 严谨档温度：问答/分析/打分等需要稳定输出的场景。 */
    private Double temperature = 0.3;

    /** 口语档温度：购物助手/在线客服等需要自然语气的场景。 */
    private Double chattyTemperature = 0.6;

    /**
     * 单次补全最大生成 token 数。
     * <p>
     * 原默认 1024 会让「营销文案 DETAIL」（≤300 字中文正文 + 3~6 卖点 + JSON 结构）间歇性撞顶：
     * 模型默认开启 thinking，<b>思考 token 与正文共用同一份预算</b>，输出会在字段中间被截断。
     * 与 {@code application.properties} 的 {@code deepseek.max-tokens} 保持同源（3072），
     * 避免该行配置被删或被环境变量覆盖时静默回退到 1024、缺陷复发。
     */
    private Integer maxTokens = 3072;

    /** 单次请求超时（秒）。 */
    private Integer timeoutSeconds = 60;

    /** 失败自动重试次数（替代原客服服务里手写的 for-retry 循环）。 */
    private Integer maxRetries = 1;

    /** 是否打印请求体（调试用）。 */
    private boolean logRequests = false;

    /** 是否打印响应体（调试用）。 */
    private boolean logResponses = false;

    // ---------------- getter / setter ----------------

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getChattyTemperature() { return chattyTemperature; }
    public void setChattyTemperature(Double chattyTemperature) { this.chattyTemperature = chattyTemperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public boolean isLogRequests() { return logRequests; }
    public void setLogRequests(boolean logRequests) { this.logRequests = logRequests; }

    public boolean isLogResponses() { return logResponses; }
    public void setLogResponses(boolean logResponses) { this.logResponses = logResponses; }
}
