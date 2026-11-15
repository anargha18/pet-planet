package com.petcommerce.delivery.persistence.repository;

import com.petcommerce.delivery.persistence.entity.DeliveryOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutboxEvent, UUID> {
    /** Oldest unpublished rows, locked for the relay transaction; rows locked by another relay run are skipped. */
    @Query(value = "SELECT * FROM delivery_outbox WHERE published_at IS NULL ORDER BY created_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<DeliveryOutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
