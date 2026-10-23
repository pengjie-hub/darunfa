package com.example.backend.controller;

import com.example.backend.ai.service.ReviewAiService;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AiReviewAnalyzeResponse;
import com.example.backend.entity.Review;
import com.example.backend.security.RequireAdmin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台评论 AI 能力接口（仅管理员）。
 * <p>
 * 全部接口均为只读/分析，无业务副作用。底层由 {@link ReviewAiService} 调用
 * LangChain4j 声明式 AI（{@code ReviewAgent}）完成，接口路径与出入参与改造前一致。
 */
@RestController
@RequestMapping("/api/admin/ai/reviews")
public class AiReviewController {

    private final ReviewAiService reviewAiService;

    public AiReviewController(ReviewAiService reviewAiService) {
        this.reviewAiService = reviewAiService;
    }

    /** 分析指定评论，情感+紧急度+理由写回 Review。 */
    @PostMapping("/{reviewId}/analyze")
    @RequireAdmin
    public ApiResponse<AiReviewAnalyzeResponse> analyze(@PathVariable Long reviewId) {
        Review r = reviewAiService.analyze(reviewId);
        AiReviewAnalyzeResponse resp = new AiReviewAnalyzeResponse(
                r.getId(), r.getAiSentiment(), r.getAiUrgency(), r.getAiReason());
        return ApiResponse.ok(resp);
    }

    /** 批量分析未分析过的评论。limit 默认 30，最大 200。 */
    @PostMapping("/batch-analyze")
    @RequireAdmin
    public ApiResponse<Map<String, Object>> batchAnalyze(@RequestParam(defaultValue = "30") int limit) {
        List<Long> ids = reviewAiService.batchAnalyze(limit);
        return ApiResponse.ok(Map.of("count", ids.size(), "ids", ids));
    }

    /** 生成客服回复草稿（2 条），不写回。 */
    @PostMapping("/{reviewId}/draft-reply")
    @RequireAdmin
    public ApiResponse<AiReviewAnalyzeResponse> draftReply(@PathVariable Long reviewId) {
        List<String> drafts = reviewAiService.draftReply(reviewId);
        AiReviewAnalyzeResponse resp = new AiReviewAnalyzeResponse();
        resp.setReviewId(reviewId);
        resp.setDrafts(drafts);
        return ApiResponse.ok(resp);
    }
}
