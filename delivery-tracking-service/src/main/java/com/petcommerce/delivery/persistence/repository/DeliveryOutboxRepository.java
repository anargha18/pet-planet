package com.petcommerce.delivery.persistence.repository;

import com.petcommerce.delivery.persistence.entity.DeliveryOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutboxEvent, UUID> {
    List<DeliveryOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
