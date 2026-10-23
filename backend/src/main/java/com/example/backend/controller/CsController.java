package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.CsMessageRequest;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.CsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前台在线客服接口（客户侧）。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code GET  /api/cs/session} —— 取（或创建）我的客服会话，返回会话 id 与状态；</li>
 *   <li>{@code GET  /api/cs/session/messages} —— 我的会话消息（时间正序）；</li>
 *   <li>{@code POST /api/cs/session/messages} —— 发一条消息，<b>由大模型生成自动回复</b>并返回；</li>
 *   <li>{@code POST /api/cs/session/close} —— 主动结束会话。</li>
 * </ul>
 * <p>
 * 本路由<b>不在放行名单</b>（{@code /api/cs/**} 既不以 {@code /api/ai/} 开头，也不在
 * {@link com.example.backend.security.AuthInterceptor#isPublic} 的白名单里），
 * 因此必须带 token，会话只能读写自己的。
 */
@RestController
@RequestMapping("/api/cs")
public class CsController {

    private final CsService csService;

    public CsController(CsService csService) {
        this.csService = csService;
    }

    @GetMapping("/session")
    public ApiResponse<Map<String, Object>> session(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(csService.myConversation(userId));
    }

    @GetMapping("/session/messages")
    public ApiResponse<List<Map<String, Object>>> messages(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        Long conversationId = asLong(csService.myConversation(userId).get("id"));
        return ApiResponse.ok(csService.myMessages(userId, conversationId));
    }

    @PostMapping("/session/messages")
    public ApiResponse<Map<String, Object>> send(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody CsMessageRequest req) {
        Long conversationId = asLong(csService.myConversation(userId).get("id"));
        return ApiResponse.ok(csService.sendFromUser(userId, conversationId,
                req == null ? null : req.getContent()));
    }

    @PostMapping("/session/close")
    public ApiResponse<Void> close(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        Long conversationId = asLong(csService.myConversation(userId).get("id"));
        csService.closeByUser(userId, conversationId);
        return ApiResponse.ok(null);
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }
}
