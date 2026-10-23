package com.example.backend.ai.assistant;

import com.example.backend.ai.config.AiModelConfig;
import com.example.backend.ai.prompt.AiPrompts;
import com.example.backend.ai.rag.RagConfig;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI 在线客服的声明式契约。
 *
 * <h3>为什么这个助手不用 ChatMemory</h3>
 * 客服会话的权威历史是 {@code cs_messages} 表（后台客服要能查看、人工客服的消息也要算作上下文），
 * 而不是 AI 记忆表。因此这里刻意<b>不挂</b> {@code chatMemoryProvider}：
 * 由 {@code CsService} 从 {@code cs_messages} 读出最近若干条、拼成文本通过 {@code {{history}}}
 * 注入 —— 这样「AI 回复 / 人工客服回复」都能作为 assistant 轮次参与上下文，语气得以延续。
 * <p>
 * 这也说明 {@code @AiService} 的装配是<b>按需组合</b>的：需要记忆就挂记忆，
 * 不需要就不挂，而不是被迫接受一套固定配置。
 *
 * <h3>为什么客服尤其需要 RAG</h3>
 * 「运费多少」「生鲜能退吗」「退款几天到账」「券为什么没叠加」——
 * 客服会话里绝大多数问题都是在问平台规则。规则答错的代价最直接：
 * 用户会把它当成平台的正式承诺。挂上 {@code contentRetriever} 后，
 * 客服的每一句回答都先经过平台规则知识库校准，而不是靠模型「大概记得」。
 * <p>
 * 注意 RAG 与「会话历史」是两回事：历史解决「这句话在说什么」（上下文连贯），
 * RAG 解决「这个问题正确答案是什么」（事实准确）。两者互补，缺一不可。
 *
 * <h3>为什么挂的是「顾客侧」检索器</h3>
 * 客服对的是顾客，只能用<b>对外口径</b>回答，所以挂 {@link RagConfig#CUSTOMER_RETRIEVER}：
 * 它按受众域过滤掉了「GMV 怎么算」「后台角色权限」这类内部小节，
 * 并且带一层意图闸门 —— 顾客问「今天有多少订单」时不再硬塞平台规则进上下文，
 * 而是干净地交给会话本身去处理。
 * 这不是「少给点资料」，而是<b>防止客服把内部口径当成对外答复说出去</b>。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.CHATTY_MODEL,
        contentRetriever = RagConfig.CUSTOMER_RETRIEVER
)
public interface CsAgent {

    /**
     * 生成一条客服回复。
     *
     * @param today    今天的日期文本（客服需要回答配送时效类问题）
     * @param customer 当前客户显示名
     * @param history  本会话已有对话的纯文本记录（已去除刚写入的最后一条客户消息）
     * @param message  客户刚发出的消息
     * @return 客服口吻的回复文本
     */
    @SystemMessage(AiPrompts.CS_AGENT)
    String reply(@V("today") String today,
                 @V("customer") String customer,
                 @V("history") String history,
                 @UserMessage String message);
}
