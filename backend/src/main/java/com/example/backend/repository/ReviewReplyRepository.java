package com.example.backend.repository;

import com.example.backend.entity.ReplyType;
import com.example.backend.entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    List<ReviewReply> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    void deleteByReviewId(Long reviewId);

    /** 该评论是否已有指定类型的回复（后台用来标记「已回复/待回复」）。 */
    boolean existsByReviewIdAndReplyType(Long reviewId, ReplyType replyType);

    /** 已有指定类型回复的评论 ID 集合（后台列表一次性标出「已回复」）。 */
    @Query("select distinct r.reviewId from ReviewReply r where r.replyType = :type")
    List<Long> findReviewIdsByReplyType(@Param("type") ReplyType type);
}
