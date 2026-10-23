package com.example.backend.repository;

import com.example.backend.entity.AdminActionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 写操作审计 / 幂等记录仓储。
 *
 * <p>查询方法按用途分组：
 * <ul>
 *   <li>执行链路：按 {@code idempotencyKey} 或 {@code proposalId} 定位一行（幂等判定与提案校验）；</li>
 *   <li>审计回查：按操作人 / 对象反查历史，供「操作日志」页与 {@code query_action_logs} 使用；</li>
 *   <li>惰性过期：扫出 {@code PROPOSED} 且已过期的行（项目无 {@code @EnableScheduling}，不走定时任务）。</li>
 * </ul>
 *
 * <p>注意：{@code idempotencyKey} 在页面入口为 {@code null}，因此<b>不要</b>用
 * {@code findByIdempotencyKey(null)} 去判「是否已执行」—— 调用方必须先判空，
 * 否则会把「页面入口」误判成「幂等命中」。
 */
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    /** 幂等键反查（唯一键，最多一行）。 */
    Optional<AdminActionLog> findFirstByIdempotencyKey(String idempotencyKey);

    /** 提案 ID 反查（唯一，最多一行）。 */
    Optional<AdminActionLog> findFirstByProposalId(String proposalId);

    /** 某操作人的最近操作（操作日志页 / 「刚才那笔是谁批的」）。 */
    List<AdminActionLog> findByOperatorIdOrderByIdDesc(Long operatorId);

    /** 某对象的操作历史（订单详情、退款详情、商品详情的「操作历史」折叠块）。 */
    List<AdminActionLog> findByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId);

    /** 某状态 + 时间窗（审计页筛选、惰性过期）。 */
    List<AdminActionLog> findByStatusAndCreatedAtBetweenOrderByIdDesc(AdminActionLog.Status status,
                                                                     LocalDateTime from,
                                                                     LocalDateTime to);

    /** 惰性过期：取所有「已过期但仍 PROPOSED」的行。 */
    List<AdminActionLog> findByStatusAndExpiresAtBefore(AdminActionLog.Status status, LocalDateTime time);

    /**
     * 「当前操作人 · 未过期 · 待确认」的提案（小运常驻入口）。
     * <p>
     * 只按 {@code operator_id} 过滤 —— 调用方的 operatorId 唯一来源是鉴权拦截器，
     * 绝不接受前端传入，因此 A 的提案永远不会出现在 B 的列表里。
     * {@code created_at DESC} 让最新准备的卡片排在最前；条数由 {@code pageable} 限制。
     */
    List<AdminActionLog> findByOperatorIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long operatorId, AdminActionLog.Status status, LocalDateTime time, Pageable pageable);

    /** 统计某人对某动作的操作次数（「这个商品最近补了几次货」）。 */
    long countByOperatorIdAndAction(Long operatorId, String action);

    /** 统计某对象的某动作被成功执行过几次。 */
    long countByTargetTypeAndTargetIdAndAction(String targetType, Long targetId, String action);
}
