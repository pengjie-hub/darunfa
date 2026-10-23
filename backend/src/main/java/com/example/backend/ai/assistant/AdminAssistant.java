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
 * 后台运营助手「小运」的声明式 AI 契约。
 *
 * <h3>这一个接口替代了什么</h3>
 * 改造前「小运」是这样跑的（{@code AiChatService} + {@code AiToolsRegistry}）：
 * <pre>
 *   for (turn = 0..4) {
 *       手工拼 body → 手工 POST → 手工解析 choices[0].message.tool_calls
 *       → 手工 JSON.parse 参数 → 手工查注册表执行工具
 *       → 手工拼 role:"tool" + tool_call_id 回传 → 再来一轮
 *   }
 * </pre>
 * 现在<bold>整个 Function Calling 主循环由框架承担</bold>：接口只声明「输入什么、输出什么」，
 * 模型决定调用哪些工具、调用几次、拿到结果后怎么组织语言，全部交给 LangChain4j。
 *
 * <h3>装配为什么是 EXPLICIT</h3>
 * 容器里有两个 {@code ChatModel}（严谨档 / 口语档）以及两套工具（后台 / 前台）。
 * 若用 {@code AUTOMATIC} 模式，框架会把<b>所有</b>工具都塞进来，小运就会看到「查我的购物车」
 * 这类前台工具、小发则可能去查全站用户统计 —— 既是能力错配也是权限风险。
 * 因此这里逐一指名：模型用严谨档、工具只挂 {@code adminDataTools} 与
 * {@code adminActionTools} 这两套后台工具。
 *
 * <h3>{@code adminActionTools} 挂了什么，为什么不破坏「模型无写权限」</h3>
 * 那 6 个 {@code prepare_*} 工具<b>一行写库代码都没有</b>，它们只产出「待确认提案」：
 * 返回值里只有 {@code action / targetId / params / precheck}，<b>连 path 字段都没有</b>。
 * 真正执行由运营点确认后调用既有的后台 REST 接口完成（页面与对话同一条路径），
 * 接口路径由服务端 {@code ActionCatalog} 白名单渲染 —— 模型无处可写，注入也无处可拼。
 *
 * <h3>为什么返回类型本批仍是 {@code String}</h3>
 * 采集提案要靠框架 {@code Result.toolExecutions()}（本轮真实发生过的每次工具调用），
 * 这一步涉及返回类型变更 + 编排层改造，属于下一批（T04）的第一步，
 * 需要单独启动验证 {@code @AiService} 代理能否正确生成，避免「一次改两件大事」时无法定位问题。
 * 本批先把工具挂上去（提示词与工具签名一起生效），返回类型保持不变。
 *
 * <h3>RAG（检索增强）为什么挂在这里</h3>
 * 小运要回答两类问题：<b>数据类</b>（今天卖了多少）和<b>制度类</b>（GMV 怎么算、发货几天算超时、
 * 退款为什么要两步审批）。前者靠 {@code adminDataTools} 查库，后者<b>库里查不到</b>——
 * 它是平台规则，只存在于文档里。挂上 {@code contentRetriever} 后，每次提问会先按语义
 * 从平台规则知识库中捞出最相关的几段塞进上下文，于是制度类问题也有据可依，
 * 而不是让模型自己「推理」出一个数字或流程来。
 *
 * <h3>为什么挂「全量」检索器而不是顾客侧那一个</h3>
 * 小运是<b>唯一</b>该看到后台口径的助手：GMV 怎么算、按天统计的自然日边界、
 * 客户接待量怎么统计、后台角色有哪几种权限——这些正是运营每天要问的。
 * 因此它用的是 {@link RagConfig#CONSOLE_RETRIEVER}（不做受众域过滤），
 * 顾客侧那层意图闸门也不挂（后台问「今天有多少订单」是本职，拦掉反而坏事）。
 * 小发与 AI 客服则相反，挂的是 {@link RagConfig#CUSTOMER_RETRIEVER} ——
 * 只有它们需要「不能把内部口径讲给顾客」这条约束。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.STRICT_MODEL,
        chatMemoryProvider = AiMemoryConfig.MEMORY_PROVIDER,
        contentRetriever = RagConfig.CONSOLE_RETRIEVER,
        tools = {"adminDataTools", "adminActionTools"}
)
public interface AdminAssistant {

    /**
     * 向小运提问。
     *
     * @param memoryKey 会话记忆标识（{@code u:<userId>}），决定加载谁的对话历史
     * @param message   管理员的自然语言问题
     * @param today     当前日期文本（如「2026年10月08日 星期四」），每次请求现算，保证「今天」永远正确
     * @param memory    该用户的长期记忆上下文（偏好 / 关键上下文），无记忆时为空串
     * @return 自然语言答复 + 本轮<b>真实发生</b>的工具调用明细（{@code toolExecutions()}）
     */
    @SystemMessage(AiPrompts.ADMIN_ASSISTANT)
    Result<String> chat(@MemoryId String memoryKey,
                @UserMessage String message,
                @V("today") String today,
                @V("memory") String memory);
}
