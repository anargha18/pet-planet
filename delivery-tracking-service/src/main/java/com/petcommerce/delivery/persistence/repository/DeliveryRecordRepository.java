package com.petcommerce.delivery.persistence.repository;

import com.petcommerce.delivery.domain.model.DeliveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {
    Optional<DeliveryRecord> findByOrderId(Long orderId);
    List<DeliveryRecord> findByCustomerId(Long customerId);
}
