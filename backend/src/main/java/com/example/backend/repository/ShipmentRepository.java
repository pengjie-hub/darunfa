package com.example.backend.repository;

import com.example.backend.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    /** 批量取（订单列表显示运单号用），避免每单一次查询。 */
    List<Shipment> findByOrderIdIn(Collection<Long> orderIds);

    /**
     * 按运单号反查（运单号冲突提示要告诉运营「已被哪一单占用」）。
     * <p>
     * 用 {@code findFirst} 而不是 {@code findBy}：{@code shipments.tracking_no} 目前
     * <b>没有唯一索引</b>（存量数据待清洗，见 PRD Q10），历史上可能存在多行同号。
     * 若用 {@code Optional<Shipment> findByTrackingNo}，一旦库里真有重复就抛
     * {@code IncorrectResultSizeDataAccessException}，把「提示冲突」变成 500。
     */
    Optional<Shipment> findFirstByTrackingNo(String trackingNo);

    /**
     * 运单号是否已被<b>其它订单</b>占用（预检与业务层都用它）。
     *
     * @param trackingNo 运单号
     * @param orderId    当前订单 ID（排除自己，允许本单修正运单号）
     */
    boolean existsByTrackingNoAndOrderIdNot(String trackingNo, Long orderId);
}
