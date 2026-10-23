package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 评论相关 AI 能力（后台评论管理 + 前台评论总结）的声明式契约。
 *
 * <h3>三类能力为什么合并到一个接口</h3>
 * 它们共用同一个模型配置（严谨档）、同一套「输入文本 → 输出文本」形态，只是提示词不同。
 * LangChain4j 允许在<b>方法级</b>覆盖类级的 {@code @SystemMessage}，
 * 因此用「一个接口 + 三个方法」表达「同一模型的不同任务」，比拆成三个接口更省事、也更容易对比调优。
 *
 * <h3>为什么返回 String 而不是结构化对象</h3>
 * LangChain4j 支持直接返回 POJO / List 并自动做 JSON 解析。这里是<b>有意不用</b>：
 * 大模型的 JSON 输出偶尔会带上 ```json 围栏、多余前后缀、或把「负面」写成「负」。
 * 既有的容错解析（正则截取 JSON 片段 + 枚举模糊匹配）就是为这些情况写的，
 * 保留它可以让「模型偶发不规范」不至于变成用户可见的 502。
 * 因此接口只负责「把提示词与文本交给模型」，结构化校验仍留在编排层。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.STRICT_MODEL
)
public interface ReviewAgent {

    /**
     * 分析一条评论的情感与紧急度。
     *
     * @param content 已拼装好的评论上下文（用户名 / 评分 / 评论正文）
     * @return 模型原始输出，期望形如 {@code {"sentiment":"负面","urgency":"紧急","reason":"..."}}
     */
    @SystemMessage(AiPrompts.REVIEW_ANALYZE)
    String analyze(@UserMessage String content);

    /**
     * 起草客服回复草稿。
     *
     * @param content 已拼装好的评论上下文
     * @return 模型原始输出，期望形如 {@code ["草稿1","草稿2"]}
     */
    @SystemMessage(AiPrompts.REVIEW_DRAFT_REPLY)
    String draftReply(@UserMessage String content);

    /**
     * 总结一批<b>用户评价</b>（调用方须先剔除商家回复）。
     *
     * @param content 商品名 + 逐行用户评价
     * @return 200 字以内的中文总结
     */
    @SystemMessage(AiPrompts.REVIEW_SUMMARY)
    String summarize(@UserMessage String content);
}
