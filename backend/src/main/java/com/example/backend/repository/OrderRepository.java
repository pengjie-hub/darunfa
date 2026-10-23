package com.example.backend.repository;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    Optional<Order> findByOrderNo(String orderNo);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.payAmount), 0) from Order o where o.status in :statuses")
    BigDecimal sumPayAmountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    /** 查询某时间点之后创建的订单（用于近 7 天趋势统计）。 */
    List<Order> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime after);

    /** 统计某用户在指定时间窗口内的订单数（用于"短时多单"检测）。 */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    /** 查某用户最近 N 条订单（用于历史均值 / P95 计算）。 */
    List<Order> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
