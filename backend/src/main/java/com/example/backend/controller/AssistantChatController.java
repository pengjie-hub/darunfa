package com.example.backend.controller;

import com.example.backend.ai.service.ShopAssistantService;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AssistantChatRequest;
import com.example.backend.dto.AssistantChatResponse;
import com.example.backend.entity.AiChatMessage;
import com.example.backend.repository.AiChatMessageRepository;
import com.example.backend.security.AuthInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大润发 AI 助手（前台用户向）。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code POST /api/assistant/chat} —— 需要登录。
 *       请求体 {@link AssistantChatRequest}，响应体 {@link AssistantChatResponse}。
 *       工具调用由 LangChain4j 自主完成，工具结果被映射为 {@code attachments} 供 App 渲染卡片。</li>
 *   <li>{@code GET /api/assistant/history} —— 需要登录。
 *       返回当前用户最近 20 条对话（时间正序），供 App 重进页面时回放气泡。</li>
 * </ul>
 * <p>
 * 本路由<b>不走放行名单</b>（{@code /api/assistant/**} 不在
 * {@link AuthInterceptor#preHandle} 的 isPublic 集合中），因此每次都会校验 token 并把 userId
 * 注入到 {@code AuthInterceptor.ATTR_USER_ID}；工具侧再通过 {@code @ToolMemoryId} 拿到它，
 * 保证「查物流 / 准备下单」只能动当前账号自己的资源，历史也只能读自己的。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    /** 回放给前端的最大消息条数（20 条 ≈ 10 轮问答）。 */
    private static final int HISTORY_LIMIT = 20;

    private final ShopAssistantService shopAssistantService;
    private final AiChatMessageRepository chatMessageRepository;

    public AssistantChatController(ShopAssistantService shopAssistantService,
                                   AiChatMessageRepository chatMessageRepository) {
        this.shopAssistantService = shopAssistantService;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantChatResponse> chat(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody AssistantChatRequest request) {
        try {
            AssistantChatResponse resp = shopAssistantService.chat(userId, request);
            return ApiResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "AI 回复失败: " + e.getMessage());
        }
    }

    /**
     * 当前用户的最近对话历史（时间正序，最多 {@value #HISTORY_LIMIT} 条）。
     * <p>
     * 「小发」的对话由 {@code ChatMemoryStore} 落库到 {@code ai_chat_messages}，
     * 本接口把那批数据按角色/文本吐回来，让前端重进页面时能<b>回放气泡</b>。
     * <p>
     * 说明：只回放角色与文本。工具卡片（商品 / 对比 / 物流 / 订单预览）是当次请求的瞬时产物，
     * 未持久化，因此历史里的 AI 消息只显示文字，不会再出现卡片。
     */
    @GetMapping("/history")
    public ApiResponse<List<Map<String, String>>> history(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        List<AiChatMessage> desc = chatMessageRepository.findTop20ByUserIdOrderByIdDesc(userId);
        if (desc == null || desc.isEmpty()) {
            return ApiResponse.ok(new ArrayList<>());
        }
        // 仓储按 id 降序返回（最新在前），倒序还原成时间正序，前端从上往下渲染即为对话顺序
        List<AiChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        int from = Math.max(0, asc.size() - HISTORY_LIMIT);
        List<Map<String, String>> out = new ArrayList<>(asc.size() - from);
        for (int i = from; i < asc.size(); i++) {
            AiChatMessage m = asc.get(i);
            Map<String, String> item = new LinkedHashMap<>(2);
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            out.add(item);
        }
        return ApiResponse.ok(out);
    }
}
