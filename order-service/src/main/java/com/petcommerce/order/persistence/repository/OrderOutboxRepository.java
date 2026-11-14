package com.petcommerce.order.persistence.repository;

import com.petcommerce.order.persistence.entity.OrderOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, UUID> {
    /** Oldest unpublished rows, locked for the relay transaction; rows locked by another relay run are skipped. */
    @Query(value = "SELECT * FROM order_outbox WHERE published_at IS NULL ORDER BY created_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OrderOutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
    Optional<OrderOutboxEvent> findByEventId(UUID eventId);
}
