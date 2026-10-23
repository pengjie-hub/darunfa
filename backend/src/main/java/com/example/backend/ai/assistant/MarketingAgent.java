package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 后台「营销文案生成」的声明式 AI 契约（商品文案 / 活动文案共用）。
 *
 * <h3>为什么用口语档模型</h3>
 * 文案要的是自然、亲切、有变化的表达，{@link AiModelConfig#CHATTY_MODEL}（温度 0.6）比严谨档更合适；
 * 合规红线不靠低温度保证，而是写死在 {@link AiPrompts#MARKETING_COPY} 里，并由
 * {@code MarketingCopyService} 再做一层绝对化用语二次过滤兜底。
 *
 * <h3>为什么无状态</h3>
 * 本接口<b>不挂</b> {@code chatMemoryProvider}、<b>不挂</b> {@code contentRetriever}、<b>不挂</b> tools：
 * 每次文案生成都是独立的一次性请求，不需要多轮记忆，也不需要检索与函数调用。
 * 这也让它成为「最小可用的 @AiService」的又一个清晰样例。
 *
 * <h3>为什么返回 String 而不是结构化对象</h3>
 * 与 {@link ReviewAgent} 同理：大模型偶尔会带 ```json 围栏、多余前后缀，或把数组写成单个字符串。
 * 结构化校验与容错解析统一放在 {@code MarketingCopyService} 里，接口只负责把提示词与文本交给模型。
 * <p>
 * <b>关于「输出被截断」</b>：文案（尤其 DETAIL 长正文）偶发会撞到模型输出 token 上限而在字段中间断掉。
 * 这里特意<b>保持 String 返回类型不变</b>（不改契约），由 {@code MarketingCopyService} 通过
 * 「输出是否出现收尾 {@code }}」这一扁平 JSON 的完整性判据来识别截断并重试，
 * 避免改动接口签名影响既有调用方与测试脚本。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.CHATTY_MODEL
)
public interface MarketingAgent {

    /**
     * 根据已拼装好的营销上下文生成文案。
     *
     * @param content 已拼装好的场景/渠道/商品或活动上下文（见 {@code MarketingCopyService#buildUserMessage}）
     * @return 模型原始输出，期望形如
     *         {@code {"title":"...","subtitle":"...","sellingPoints":["a","b"],"body":"...","cta":"..."}}
     */
    @SystemMessage(AiPrompts.MARKETING_COPY)
    String generate(@UserMessage String content);
}
