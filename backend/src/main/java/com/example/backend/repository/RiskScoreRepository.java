package com.example.backend.repository;

import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    /**
     * 取某订单最新一条评分（按创建时间降序，id 兜底避免同毫秒问题）。
     */
    @Query("select r from RiskScore r where r.orderId = ?1 order by r.createdAt desc, r.id desc")
    List<RiskScore> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    default Optional<RiskScore> findLatestByOrderId(Long orderId) {
        List<RiskScore> list = findByOrderIdOrderByCreatedAtDesc(orderId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 列出所有评分，按分数降序 / 创建时间降序。
     */
    List<RiskScore> findAllByOrderByScoreDescCreatedAtDesc();

    /**
     * 按风险等级筛选，按分数降序。
     */
    List<RiskScore> findByLevelOrderByScoreDescCreatedAtDesc(RiskLevel level);

    /**
     * 统计各等级数量。
     */
    long countByLevel(RiskLevel level);

    /**
     * 统计未读评分数量（用于红点提醒）。
     */
    long countByIsReadFalse();

    /** 统计某等级的未读数量（一键已读的卡片要按等级分列，不能只报一个总数）。 */
    long countByLevelAndIsReadFalse(RiskLevel level);

    /**
     * 未读集合的「指纹」：条数 + 最大 id + 分数和。
     *
     * <p>用途只有一个 —— 当「风险消息一键已读」的幂等键。三种变化必须能区分开：
     * 新增了一条未读（cnt/maxId 变）、运营手动读掉一条（cnt 变）、
     * 老记录被读掉同时来了新记录（cnt 可能不变，但 maxId/scoreSum 会变）。
     */
    interface UnreadStat {
        /** 未读条数。 */
        Long getCnt();

        /** 未读记录里的最大主键（增量信号）。 */
        Long getMaxId();

        /** 未读记录的分数总和（同条数换批时兜底区分）。 */
        Long getScoreSum();
    }

    /** 全部未读的指纹。 */
    @Query("select count(r) as cnt, max(r.id) as maxId, sum(r.score) as scoreSum "
            + "from RiskScore r where r.isRead = false")
    UnreadStat unreadStatAll();

    /** 指定等级未读的指纹。 */
    @Query("select count(r) as cnt, max(r.id) as maxId, sum(r.score) as scoreSum "
            + "from RiskScore r where r.isRead = false and r.level = :level")
    UnreadStat unreadStatByLevel(@Param("level") RiskLevel level);

    /** 批量置已读（全部）。返回受影响行数。 */
    @Modifying
    @Query("update RiskScore r set r.isRead = true where r.isRead = false")
    int markAllRead();

    /** 批量置已读（仅指定等级）。返回受影响行数。 */
    @Modifying
    @Query("update RiskScore r set r.isRead = true where r.isRead = false and r.level = :level")
    int markAllReadByLevel(@Param("level") RiskLevel level);
}