package com.example.backend.controller;

import com.example.backend.ai.service.SimpleChatService;
import com.example.backend.dto.ChatRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用 AI 对话接口示例。
 * <p>
 * POST /api/chat  请求体：{ "message": "你好" }  响应体：{ "reply": "AI 回复" }
 * GET  /api/ping  健康检查，返回 { "status": "ok" }，用于确认服务已启动。
 * <p>
 * 这是「最小可用的 LangChain4j 接入样例」，也是改造后唯一一个无记忆、无工具的 AI 端点。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final SimpleChatService simpleChatService;

    public ChatController(SimpleChatService simpleChatService) {
        this.simpleChatService = simpleChatService;
    }

    /**
     * 接收用户消息并返回 AI 回复。
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息内容为空"));
        }

        try {
            String reply = simpleChatService.chat(message);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Key 未配置等配置类错误
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI 回复失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查：浏览器直接访问 http://localhost:8080/api/ping 验证服务是否已启动。
     */
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
