package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.Shipment;
import com.example.backend.entity.ShipmentStatus;
import com.example.backend.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流服务：查询与状态更新。
 */
@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public List<Shipment> listAll() {
        return shipmentRepository.findAll();
    }


    /** 更新物流状态（已发货 → 运输中 → 已送达）。 */
    @Transactional
    public Shipment updateStatus(Long shipmentId, ShipmentStatus status) {
        Shipment s = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException(404, "物流记录不存在"));
        s.setStatus(status);
        if (status == ShipmentStatus.DELIVERED && s.getDeliveredAt() == null) {
            s.setDeliveredAt(LocalDateTime.now());
        }
        return shipmentRepository.save(s);
    }
}
