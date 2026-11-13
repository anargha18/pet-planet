package com.petcommerce.shipping.persistence.repository;

import com.petcommerce.shipping.persistence.entity.ShipmentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentOutboxRepository extends JpaRepository<ShipmentOutboxEvent, UUID> {
    List<ShipmentOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
