package com.example.backend.repository;

import com.example.backend.entity.RefundRequest;
import com.example.backend.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findAllByOrderByIdDesc();

    List<RefundRequest> findByStatusOrderByIdDesc(RefundStatus status);

    /** 批量取（订单列表拼退款状态用），按 id 倒序以便「每个订单取第一条即为最新」。 */
    List<RefundRequest> findByOrderIdInOrderByIdDesc(Collection<Long> orderIds);

    /** 某订单最新一条退款申请（订单详情用）。 */
    Optional<RefundRequest> findFirstByOrderIdOrderByIdDesc(Long orderId);

    long countByStatus(RefundStatus status);
}
