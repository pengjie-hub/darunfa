package com.example.backend.controller;

import com.example.backend.ai.service.SummaryAiService;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AiSummaryRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 评论总结接口（前台商品详情页使用）。
 * <p>
 * POST /api/ai/summary
 * 请求体：{ "productName": "商品名", "reviews": ["评论1", "评论2", ...] }
 * 响应体（统一包装）：{ "code":0, "message":"ok", "data": { "summary": "AI 生成的总结文本" } }
 * <p>
 * 与改造前完全一致的契约；底层从手写 HTTP 调用换成了 LangChain4j 声明式 AI（{@code ReviewAgent}）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiSummaryController {

    private final SummaryAiService summaryAiService;

    public AiSummaryController(SummaryAiService summaryAiService) {
        this.summaryAiService = summaryAiService;
    }

    @PostMapping("/summary")
    public ApiResponse<Map<String, String>> summary(@RequestBody AiSummaryRequest request) {
        // 只总结「用户发表」的评价：商家/客服回复一律剔除（AI 只应反映用户真实体验）
        List<String> reviews = SummaryAiService.stripNonUserLines(request.getReviews());
        if (reviews.isEmpty()) {
            return ApiResponse.error(400, "没有可总结的用户评价（评论列表为空或只含商家回复）");
        }

        try {
            String summary = summaryAiService.summarize(request.getProductName(), reviews);
            return ApiResponse.ok(Map.of("summary", summary));
        } catch (Exception e) {
            return ApiResponse.error(500, "AI 总结失败: " + e.getMessage());
        }
    }
}
