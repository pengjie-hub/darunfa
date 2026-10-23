package com.example.backend.repository;

import com.example.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.productId = :productId")
    double averageRating(@Param("productId") Long productId);

    /** 待 AI 分析的评论（按 id 倒序，限制 200 条用于批量分析）。 */
    List<Review> findTop200ByAiAnalyzedAtIsNullOrderByIdDesc();

    /** 后台评论列表（按 id 倒序）。 */
    List<Review> findAllByOrderByIdDesc();

    /**
     * 该用户已评价过的商品 ID 集合（前台「待评价」判定用）。
     * <p>
     * 业务规则：同一个用户对同一个商品只能有一条评论，因此「订单是否已评价」
     * 等价于「订单里的商品是否都已被该用户评价过」。
     */
    @Query("select distinct r.productId from Review r where r.userId = :userId and r.productId in :productIds")
    List<Long> findReviewedProductIds(@Param("userId") Long userId,
                                     @Param("productIds") Collection<Long> productIds);
}
