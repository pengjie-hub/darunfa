package com.example.backend.ai.service;

import com.example.backend.ai.assistant.ShopAssistant;
import com.example.backend.ai.memory.JpaChatMemoryStore;
import com.example.backend.ai.memory.LongTermMemoryStore;
import com.example.backend.ai.prompt.AiPrompts;
import com.example.backend.dto.AssistantChatRequest;
import com.example.backend.dto.AssistantChatResponse;
import com.example.backend.dto.AssistantChatResponse.ChatAttachmentDTO;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「小发」（前台 AI 购物助手）编排服务。
 *
 * <h3>它负责的、框架不负责的事</h3>
 * 框架把「模型说了什么」交给我们（{@code Result.content()}），
 * 但 App 还需要「模型刚刚查到了什么」来渲染卡片。因此本类的核心职责是：
 * 把 {@link Result#toolExecutions()} 映射成前端约定的 {@code attachments}。
 *
 * <h3>工具 → 卡片的映射表</h3>
 * <table border="1">
 *   <tr><th>工具名</th><th>attachment.type</th><th>前端卡片</th></tr>
 *   <tr><td>{@code search_user_products}</td><td>{@code products}</td><td>商品横滑</td></tr>
 *   <tr><td>{@code compare_user_products}</td><td>{@code comparison}</td><td>参数对比表</td></tr>
 *   <tr><td>{@code query_user_logistics}</td><td>{@code logistics}</td><td>物流时间线</td></tr>
 *   <tr><td>{@code list_user_cart} / {@code preview_user_order}</td><td>{@code order_preview}</td><td>下单预览</td></tr>
 *   <tr><td>{@code remember_user_fact}</td><td>—</td><td>只写库不展示</td></tr>
 * </table>
 * <p>
 * 与改造前的关键差异：原先要在手写主循环里「边执行工具边收集」，现在工具执行由框架完成，
 * 本类只做一次纯粹的「事后映射 + 按类型合并」—— 同一类型被多次调用时合并进同一张卡片的数组里。
 */
@Service
public class ShopAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ShopAssistantService.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA);

    private final ShopAssistant assistant;
    private final LongTermMemoryStore longTermMemoryStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShopAssistantService(ShopAssistant assistant, LongTermMemoryStore longTermMemoryStore) {
        this.assistant = assistant;
        this.longTermMemoryStore = longTermMemoryStore;
    }

    /**
     * 与「小发」对话一轮。
     *
     * @param userId 当前登录用户；为 {@code null} 表示匿名（工具会拒绝涉及个人数据的调用）
     * @param req    请求体（消息 + 可选的临时历史 + 是否会话首条）
     */
    public AssistantChatResponse chat(@Nullable Long userId, AssistantChatRequest req) {
        if (req == null || req.getMessage() == null || req.getMessage().isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        String today = LocalDate.now().format(DATE_FMT);
        String greeting = req.isSessionFirst() ? AiPrompts.SHOP_GREETING_FIRST : AiPrompts.SHOP_GREETING_NORMAL;
        String memory = userId == null ? "" : longTermMemoryStore.buildContext(userId);

        // 历史上下文来源二选一，避免同一轮对话被注入两次：
        //   - 已登录：由 ChatMemoryStore 从数据库加载（含跨会话记忆，是权威来源）→ 此处传空
        //   - 匿名：没有可用的持久记录，回退到前端上传的临时历史
        String history = (userId == null) ? renderHistory(req.safeHistory()) : "";

        // 登录用户按 userId 隔离记忆；匿名使用一次性 key，保证不会写进任何真实用户的历史
        String memoryKey = (userId == null)
                ? JpaChatMemoryStore.anonKey()
                : JpaChatMemoryStore.userKey(userId);

        Result<String> result = assistant.chat(memoryKey, req.getMessage().trim(),
                today, greeting, memory, history);

        String reply = result.content();
        if (reply == null || reply.isBlank()) {
            reply = "(助手未生成回复，请重试)";
        }
        return new AssistantChatResponse(reply, buildAttachments(result.toolExecutions()));
    }

    // =====================================================================
    //  工具结果 → 前端附件
    // =====================================================================

    /** 把本轮工具调用明细映射成前端卡片列表（按首次出现顺序，同类型合并）。 */
    private List<ChatAttachmentDTO> buildAttachments(List<ToolExecution> executions) {
        List<ChatAttachmentDTO> attachments = new ArrayList<>();
        if (executions == null || executions.isEmpty()) return attachments;

        Map<String, Integer> indexByType = new LinkedHashMap<>();
        for (ToolExecution execution : executions) {
            if (execution == null || execution.request() == null) continue;

            String type = attachmentType(execution.request().name());
            if (type == null) continue;   // remember_user_fact 等只写库的工具不生成卡片

            JsonNode data = parseJson(execution.result());
            if (data == null || !data.isObject() || data.has("error")) continue;

            Integer idx = indexByType.get(type);
            if (idx == null) {
                indexByType.put(type, attachments.size());
                attachments.add(new ChatAttachmentDTO(type, data));
            } else {
                // 同一类型被多次调用 → 合并到一个数组里，前端按数组渲染多张/多段
                ChatAttachmentDTO existing = attachments.get(idx);
                ArrayNode merged = objectMapper.createArrayNode();
                if (existing.getData() != null && existing.getData().isArray()) {
                    merged.addAll((ArrayNode) existing.getData());
                } else if (existing.getData() != null) {
                    merged.add(existing.getData());
                }
                merged.add(data);
                attachments.set(idx, new ChatAttachmentDTO(type, merged));
            }
        }
        return attachments;
    }

    /** 工具名 → 前端 attachment type；返回 {@code null} 表示不生成卡片。 */
    private String attachmentType(String toolName) {
        if (toolName == null) return null;
        return switch (toolName) {
            case "search_user_products" -> "products";
            case "compare_user_products" -> "comparison";
            case "query_user_logistics" -> "logistics";
            // 读购物车与下单预览共用前端同一张 order_preview 卡片
            case "list_user_cart", "preview_user_order" -> "order_preview";
            default -> null;
        };
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.debug("工具结果不是合法 JSON，跳过附件渲染: {}", raw);
            return null;
        }
    }

    /**
     * 把前端上传的临时历史渲染成提示词里的一段文本（仅匿名场景使用）。
     */
    private String renderHistory(List<AssistantChatRequest.HistoryItem> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n[本会话已有的对话记录]\n");
        for (AssistantChatRequest.HistoryItem item : history) {
            if (item == null || item.getContent() == null || item.getContent().isBlank()) continue;
            if (item.getRole() == null) continue;
            String speaker = "user".equalsIgnoreCase(item.getRole()) ? "用户" : "小发";
            sb.append(speaker).append("：").append(item.getContent().trim()).append("\n");
        }
        return sb.length() > 0 ? sb.toString() : "";
    }
}
