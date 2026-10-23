package com.example.backend.repository;

import com.example.backend.entity.Promotion;
import com.example.backend.entity.PromotionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByStatusOrderByCreatedAtDesc(Integer status);


    List<Promotion> findAllByOrderByCreatedAtDesc();

    /**
     * 同源活动查重（供 {@code PromotionService.findDuplicates} 使用）。
     *
     * <h3>为什么不用派生查询而写 @Query</h3>
     * 派生查询在 {@code productId = null} 时会生成 {@code product_id = null}，
     * 永远不成立 —— 而「全店通用」正是最常见的场景。这里显式处理 null，
     * 让「全店 vs 全店」也能命中。
     *
     * <p>阈值/优惠值按 SQL 数值比较（{@code 200.0} 与 {@code 200.00} 视为相等），
     * 所以调用方不必先把 scale 归一化。
     *
     * @param type      活动类型
     * @param threshold 门槛金额
     * @param discount  满减金额或折扣率
     * @param productId 指定商品；null 表示全店通用
     */
    @Query("""
            select p from Promotion p
             where p.type = :type
               and p.threshold = :threshold
               and p.discount = :discount
               and ((:productId is null and p.productId is null) or p.productId = :productId)
            """)
    List<Promotion> findByTypeAndThresholdAndDiscountAndProductId(
            @Param("type") PromotionType type,
            @Param("threshold") BigDecimal threshold,
            @Param("discount") BigDecimal discount,
            @Param("productId") Long productId);
}
