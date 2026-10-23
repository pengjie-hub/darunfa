package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiMemoryConfig;
import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import com.example.backend.ai.rag.RagConfig;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 前台购物助手「小发」的声明式 AI 契约。
 *
 * <h3>为什么返回 {@link Result} 而不是 String</h3>
 * 阅读接口签名即可看出：小发不只要回一句话，App 还要渲染「商品横滑卡 / 参数对比表 / 物流时间线 /
 * 订单预览卡」。这些卡片的数据来自工具的结构化结果，而不是模型的话。
 * <p>
 * {@link Result} 是 LangChain4j 给的「带副产品的返回值」：{@code content()} 是自然语言答复，
 * {@code toolExecutions()} 则是本轮真实发生过的每一次工具调用（含工具名与原始 JSON 结果）。
 * 编排层据此把结果映射成前端 {@code attachments} —— 于是「卡片」不再需要手写解析
 * {@code finish_reason == "tool_calls"} 的响应体，也不会出现「模型说调用过、其实没调用」的错位。
 *
 * <h3>记忆与工具</h3>
 * 模型用口语档（温度 0.6），只挂 {@code shopTools}；
 * 工具内部通过 {@code @ToolMemoryId} 拿到当前用户，模型既看不到也伪造不了 userId。
 *
 * <h3>RAG（检索增强）为什么挂在这里</h3>
 * 小发被问得最多的一类问题不是「有哪些商品」，而是<b>规则</b>：
 * 「满多少免运费」「生鲜能不能退」「我的券怎么没叠加」「退款几天到账」。
 * 这些内容不在商品表里，也不该由模型自由发挥 —— 答错会被用户当成平台的承诺。
 * 挂上 {@code contentRetriever} 后，这类问题会先召回平台规则原文再作答，
 * 而「查商品」「查物流」这类需要实时数据的问题仍然由工具查库，两者各司其职。
 *
 * <h3>为什么挂的是「顾客侧」检索器</h3>
 * 小发对顾客说话，只能引用<b>对外口径</b>，所以挂 {@link RagConfig#CUSTOMER_RETRIEVER} ——
 * 它按受众域过滤掉后台内部小节，并带一层意图闸门（问「有多少订单」这类数字问题时
 * 干脆不检索，避免拿平台规则去答一个计数问题）。
 * 挂错了域的实际后果是：顾客会从助手嘴里听到「GMV 计入退款中订单」这种内部口径。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.CHATTY_MODEL,
        chatMemoryProvider = AiMemoryConfig.MEMORY_PROVIDER,
        contentRetriever = RagConfig.CUSTOMER_RETRIEVER,
        tools = {"shopTools"}
)
public interface ShopAssistant {

    /**
     * 与「小发」对话一轮（内部可能发生多次工具调用）。
     *
     * @param memoryKey 会话记忆标识：登录用户为 {@code u:<userId>}，匿名为 {@code anon:<uuid>}（不落库）
     * @param message   用户输入
     * @param today     当前日期文本（每次现算）
     * @param greeting  首轮 / 非首轮的提示词片段，由 {@code sessionFirst} 决定
     * @param memory    长期记忆上下文
     * @param history   匿名场景下前端临时上传的历史文本（登录用户走数据库记忆，此处传空串）
     * @return 自然语言答复 + 本轮工具调用明细
     */
    @SystemMessage(AiPrompts.SHOP_ASSISTANT)
    Result<String> chat(@MemoryId String memoryKey,
                        @UserMessage String message,
                        @V("today") String today,
                        @V("greeting") String greeting,
                        @V("memory") String memory,
                        @V("history") String history);
}
