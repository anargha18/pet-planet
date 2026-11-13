package com.petcommerce.order.persistence.repository;

import com.petcommerce.order.persistence.entity.OrderOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, UUID> {
    List<OrderOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
    Optional<OrderOutboxEvent> findByEventId(UUID eventId);
}
