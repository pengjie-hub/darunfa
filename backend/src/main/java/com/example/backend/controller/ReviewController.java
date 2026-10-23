package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AddReplyRequest;
import com.example.backend.dto.AddReviewRequest;
import com.example.backend.entity.Review;
import com.example.backend.entity.ReviewReply;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.ReviewService;
import jakarta.validation.Valid;
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
 * 评论与追评接口。
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<Review> add(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                   @Valid @RequestBody AddReviewRequest req) {
        return ApiResponse.ok(reviewService.addReview(userId, req));
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<List<Review>> listByProduct(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.listByProduct(productId));
    }

    @GetMapping("/product/{productId}/stats")
    public ApiResponse<Map<String, Object>> stats(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.stats(productId));
    }

    @GetMapping("/{reviewId}/replies")
    public ApiResponse<List<ReviewReply>> replies(@PathVariable Long reviewId) {
        return ApiResponse.ok(reviewService.listReplies(reviewId));
    }

    @PostMapping("/{reviewId}/reply")
    public ApiResponse<ReviewReply> addReply(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                             @PathVariable Long reviewId,
                                             @Valid @RequestBody AddReplyRequest req) {
        return ApiResponse.ok(reviewService.addReply(userId, reviewId, req));
    }
}
