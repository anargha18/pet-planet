package com.petcommerce.fulfillment.persistence.repository;

import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulfillmentRecordRepository extends JpaRepository<FulfillmentRecord, UUID> {
    Optional<FulfillmentRecord> findByOrderId(Long orderId);
    List<FulfillmentRecord> findByCustomerId(Long customerId);
}
