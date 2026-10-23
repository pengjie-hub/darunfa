package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.AddReplyRequest;
import com.example.backend.dto.AddReviewRequest;
import com.example.backend.entity.Review;
import com.example.backend.entity.ReplyType;
import com.example.backend.entity.ReviewReply;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.ReviewReplyRepository;
import com.example.backend.repository.ReviewRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论与追评服务。
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewReplyRepository replyRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.replyRepository = replyRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Review addReview(Long userId, AddReviewRequest req) {
        productRepository.findById(req.getProductId())
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (reviewRepository.existsByUserIdAndProductId(userId, req.getProductId())) {
            throw new BusinessException(409, "您已评价过该商品");
        }
        String userName = userRepository.findById(userId).map(u ->
                u.getNickname() != null ? u.getNickname() : u.getUsername()).orElse("用户");

        Review review = new Review();
        review.setProductId(req.getProductId());
        review.setUserId(userId);
        review.setUserName(userName);
        review.setRating(req.getRating() == null ? BigDecimal.valueOf(5.0)
                : BigDecimal.valueOf(req.getRating()));
        review.setContent(req.getContent());
        return reviewRepository.save(review);
    }

    public List<Review> listByProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<ReviewReply> listReplies(Long reviewId) {
        return replyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
    }

    @Transactional
    public ReviewReply addReply(Long userId, Long reviewId, AddReplyRequest req) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        String userName = userRepository.findById(userId).map(u ->
                u.getNickname() != null ? u.getNickname() : u.getUsername()).orElse("用户");

        ReviewReply reply = new ReviewReply();
        reply.setReviewId(review.getId());
        reply.setUserId(userId);
        reply.setUserName(userName);
        reply.setContent(req.getContent());
        reply.setReplyType(ReplyType.USER);
        return replyRepository.save(reply);
    }

    /**
     * 商家回复：后台客服/运营对某条评论的正式回复。
     * <p>
     * 与用户追评同表（{@code review_replies}），靠 {@link ReplyType#MERCHANT} 区分，
     * 这样前台评论列表能在同一条评论下按时间顺序把「用户追评 / 商家回复」一起展示。
     *
     * @param operatorId   后台操作人 ID
     * @param operatorName 后台操作人显示名（后台账号通常没有 nickname，取 username）
     */
    @Transactional
    public ReviewReply addMerchantReply(Long operatorId, String operatorName, Long reviewId, String content) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "回复内容不能为空");
        }
        String name = (operatorName == null || operatorName.isBlank()) ? "商家" : operatorName;

        ReviewReply reply = new ReviewReply();
        reply.setReviewId(review.getId());
        reply.setUserId(operatorId);
        reply.setUserName(name);
        reply.setContent(content.trim());
        reply.setReplyType(ReplyType.MERCHANT);
        return replyRepository.save(reply);
    }

    /** 某条评论下的全部回复（含商家回复），时间正序。 */
    public List<ReviewReply> listRepliesOrdered(Long reviewId) {
        return replyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
    }


    /** 已有商家回复的评论 ID 集合（后台一次性拉取，用于列表打「已回复」标）。 */
    public Set<Long> merchantRepliedReviewIds() {
        return new HashSet<>(replyRepository.findReviewIdsByReplyType(ReplyType.MERCHANT));
    }

    public Map<String, Object> stats(Long productId) {
        return Map.of(
                "count", reviewRepository.countByProductId(productId),
                "averageRating", reviewRepository.averageRating(productId)
        );
    }

    // ---------- 后台评论管理 ----------

    public List<Review> listAll() {
        return reviewRepository.findAll();
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        // 级联删除该评论下的追评
        replyRepository.deleteByReviewId(review.getId());
        reviewRepository.delete(review);
    }
}
