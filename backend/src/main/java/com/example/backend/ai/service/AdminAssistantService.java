package com.example.backend.ai.service;

import com.example.backend.ai.assistant.AdminAssistant;
import com.example.backend.ai.memory.JpaChatMemoryStore;
import com.example.backend.ai.memory.LongTermMemoryStore;
import com.example.backend.dto.ActionProposal;
import com.example.backend.dto.AiChatResponse;
import com.example.backend.entity.AiMemory;
import com.example.backend.service.AdminActionService;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「小运」（后台 AI 运营助手）编排服务。
 *
 * <h3>改造前后对比</h3>
 * <table border="1">
 *   <tr><th>职责</th><th>改造前</th><th>改造后（T04）</th></tr>
 *   <tr><td>Function Calling 主循环（最多 5 轮）</td><td>手写 for + 解析 tool_calls</td><td>框架自动完成</td></tr>
 *   <tr><td>工具 Schema</td><td>645 行手写 JSON</td><td>{@code @Tool} 注解生成</td></tr>
 *   <tr><td>最近 5 轮历史</td><td>手写 SQL 取 20 条 + 逆序 + take</td><td>{@code ChatMemoryStore} + 窗口</td></tr>
 *   <tr><td>「待确认提案」采集</td><td colspan="2">从 {@code Result.toolExecutions()} 事后映射（本轮真实发生过的调用）</td></tr>
 * </table>
 *
 * <h3>为什么提案必须从 {@code toolExecutions()} 取，而不是信模型的话</h3>
 * 模型完全可能说「我已经帮你准备好退款卡片了」而<b>实际一次工具都没调</b>
 *（这在提示词被注入、或模型偷懒时很容易发生）。若照着模型的话渲染卡片，
 * 就会出现「点确认后发现根本没这回事」——比不显示卡片糟得多。
 * <p>
 * {@link Result#toolExecutions()} 是框架记录的<b>事实</b>：本轮真调用过的工具 + 真实返回串。
 * 我们只把 {@code prepare_*} 的返回串丢给 {@link AdminActionService} 编译成提案，
 * 于是「模型说调用过、其实没调用」的结果是 <b>0 张卡片</b>，而不是一张假卡片。
 *
 * <h3>本类剩下什么</h3>
 * 只做领域决策：日期按 {@code Locale.CHINA} 现算（保证「今天」永远正确）、
 * 长期记忆注入、以及把工具事实翻译成前端要的 {@code proposals}。
 */
@Service
public class AdminAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AdminAssistantService.class);

    /** 日期格式与原实现完全一致：2026年09月11日 星期五。 */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA);

    /** 只有以该前缀开头的工具才可能产出「待确认提案」。 */
    private static final String PREPARE_TOOL_PREFIX = "prepare_";

    /**
     * 「小运在追问信息」的口语特征（用于 needsClarification）。
     * <p>
     * 必须同时覆盖「你」和「您」两种称谓：小运对运营说话时多用「您」，
     * 只写「需要你」会漏判（真机实测：「需要您先确认两点」曾被判成非追问）。
     */
    private static final String[] CLARIFY_HINTS = {
            "请提供", "请补充", "请告诉", "请确认", "请选择",
            "需要你", "需要您", "还需要", "给我", "告诉我",
            "缺失", "还缺", "哪一个", "哪个", "是哪个", "多少件", "哪一单", "哪款"
    };

    private final AdminAssistant assistant;
    private final LongTermMemoryStore longTermMemoryStore;
    private final JpaChatMemoryStore chatMemoryStore;
    private final AdminActionService adminActionService;

    public AdminAssistantService(AdminAssistant assistant,
                                 LongTermMemoryStore longTermMemoryStore,
                                 JpaChatMemoryStore chatMemoryStore,
                                 AdminActionService adminActionService) {
        this.assistant = assistant;
        this.longTermMemoryStore = longTermMemoryStore;
        this.chatMemoryStore = chatMemoryStore;
        this.adminActionService = adminActionService;
    }

    /**
     * 向小运提问并取得答复（含本轮待确认提案）。
     * <p>本轮问答由框架自动写入会话记忆（{@code ai_chat_messages}），无需再手工 saveExchange。
     * <p>
     * <b>身份说明</b>：工具内部的 operatorId 来自 {@code AdminActionContext}（Controller 已绑定），
     * 本方法<b>不</b>把它作为参数往下传 —— 少一条传递路径，就少一处能被误用的地方。
     *
     * @param userId      当前登录的后台用户（只用于记忆隔离与长期记忆）
     * @param userMessage 运营的自然语言问题
     */
    public AiChatResponse ask(Long userId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new AiChatResponse("请输入你的问题。");
        }
        String today = LocalDate.now().format(DATE_FMT);
        String memory = userId == null ? "" : longTermMemoryStore.buildContext(userId);
        String memoryKey = userId == null ? JpaChatMemoryStore.anonKey() : JpaChatMemoryStore.userKey(userId);

        Result<String> result = assistant.chat(memoryKey, userMessage.trim(), today, memory);

        String answer = result.content();
        if (answer == null || answer.isBlank()) {
            answer = "（小运未生成答复，请重试）";
        }
        List<ActionProposal> proposals = buildProposals(result.toolExecutions());
        return new AiChatResponse(answer, proposals, needsClarification(answer, proposals));
    }

    // =====================================================================
    //  工具事实 → 提案
    // =====================================================================

    /**
     * 只取 {@code prepare_*} 的真实返回串，交给 {@link AdminActionService} 编译。
     * <p>
     * 非 {@code prepare_*} 工具（{@code adminDataTools} 的查询类）一律忽略 ——
     * 它们是「给模型看的数据」，不该变成卡片。
     * <p>
     * 编译失败<b>不会</b>影响答复：{@code buildProposals} 内部逐条 try/catch，
     * 脏数据只丢那一张卡片（详见该方法注释）。
     */
    private List<ActionProposal> buildProposals(List<ToolExecution> executions) {
        List<String> prepareResults = new ArrayList<>();
        if (executions == null || executions.isEmpty()) {
            return List.of();
        }
        for (ToolExecution execution : executions) {
            if (execution == null || execution.request() == null) continue;
            String name = execution.request().name();
            if (name == null || !name.startsWith(PREPARE_TOOL_PREFIX)) continue;
            String payload = execution.result();
            if (payload == null || payload.isBlank()) continue;
            prepareResults.add(payload);
        }
        if (prepareResults.isEmpty()) {
            return List.of();
        }
        try {
            // chatMessageId 传 null：本方法拿不到本轮消息 ID；审计仍能靠 proposal_id / operator 定位
            return adminActionService.buildProposals(prepareResults, null);
        } catch (Exception e) {
            // 兜底：提案编译整体失败绝不能把答复也搞没了
            log.warn("提案编译整体失败，本轮不返回卡片: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 是否在向运营追问信息。
     * <p>
     * 判据刻意保守：<b>必须同时满足</b>「没有生成任何提案」+「答复里既有问号又有追问词」。
     * 只凭问号会误判（问答结尾常有「需要我帮您看看近 7 天趋势吗？」），
     * 只凭追问词也会误判（「同比缺失」）。
     *
     * @return {@code true} / {@code null}（null 表示不输出该字段，保持老响应体干净）
     */
    private Boolean needsClarification(String answer, List<ActionProposal> proposals) {
        if (proposals != null && !proposals.isEmpty()) return null;
        if (answer == null) return null;
        boolean ask = answer.contains("？") || answer.contains("?");
        if (!ask) return null;
        for (String hint : CLARIFY_HINTS) {
            if (answer.contains(hint)) return Boolean.TRUE;
        }
        return null;
    }

    // ---------------- 记忆管理（供后台「清空记忆 / 记忆列表」使用） ----------------

    /** 清空该用户的全部记忆：对话历史 + 长期记忆。 */
    public void clearMemory(Long userId) {
        if (userId == null) return;
        chatMemoryStore.deleteMessages(JpaChatMemoryStore.userKey(userId));
        longTermMemoryStore.clear(userId);
    }

    /** 列出该用户的长期记忆（按更新时间降序）。 */
    public List<AiMemory> listMemories(Long userId) {
        return longTermMemoryStore.list(userId);
    }

    /** 写入 / 覆盖一条长期记忆。 */
    public void saveMemory(Long userId, String key, String value) {
        longTermMemoryStore.save(userId, key, value);
    }
}
