package com.petcommerce.delivery.repository;

import com.petcommerce.delivery.entity.DeliveryOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutboxEvent, UUID> {
    List<DeliveryOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
