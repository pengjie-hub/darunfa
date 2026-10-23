package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.CsMessageRequest;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.RequirePermission;
import com.example.backend.service.CsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台客服信息管理接口。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code GET  /api/admin/cs/conversations} —— 全部客户会话（含未读数、状态）；</li>
 *   <li>{@code GET  /api/admin/cs/conversations/{id}/messages} —— 某会话的完整对话
 *       （客户提问 + AI 自动回复 + 人工回复），打开即清零未读；</li>
 *   <li>{@code POST /api/admin/cs/conversations/{id}/reply} —— 客服直接回复客户；</li>
 *   <li>{@code POST /api/admin/cs/conversations/{id}/handback} —— 把会话交还给 AI 自动应答；</li>
 *   <li>{@code GET  /api/admin/cs/stats} —— 会话总数 / 待人工数 / 未读总数（侧边栏红点）。</li>
 * </ul>
 * 统一要求 {@code cs:manage} 权限（ADMIN / OPS / CS 三类角色均已 seed）。
 */
@RestController
@RequestMapping("/api/admin/cs")
public class CsAdminController {

    private final CsService csService;

    public CsAdminController(CsService csService) {
        this.csService = csService;
    }

    @GetMapping("/conversations")
    @RequirePermission({"cs:manage"})
    public ApiResponse<List<Map<String, Object>>> conversations() {
        return ApiResponse.ok(csService.adminList());
    }

    @GetMapping("/conversations/{id}/messages")
    @RequirePermission({"cs:manage"})
    public ApiResponse<List<Map<String, Object>>> messages(@PathVariable Long id) {
        return ApiResponse.ok(csService.adminMessages(id));
    }

    @PostMapping("/conversations/{id}/reply")
    @RequirePermission({"cs:manage"})
    public ApiResponse<Map<String, Object>> reply(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @RequestAttribute(AuthInterceptor.ATTR_ROLE) String operatorRole,
            @PathVariable Long id,
            @RequestBody CsMessageRequest req) {
        String name = switch (operatorRole == null ? "" : operatorRole) {
            case "ADMIN" -> "客服（管理员）";
            case "OPS" -> "客服（运营）";
            default -> "客服";
        };
        return ApiResponse.ok(csService.adminReply(operatorId, name, id,
                req == null ? null : req.getContent()));
    }

    @PostMapping("/conversations/{id}/handback")
    @RequirePermission({"cs:manage"})
    public ApiResponse<Map<String, Object>> handBack(@PathVariable Long id) {
        return ApiResponse.ok(csService.adminHandBackToAi(id));
    }

    @GetMapping("/stats")
    @RequirePermission({"cs:manage"})
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(csService.adminStats());
    }
}
