package com.petcommerce.shipping.persistence.repository;

import com.petcommerce.shipping.persistence.entity.ShipmentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShipmentOutboxRepository extends JpaRepository<ShipmentOutboxEvent, UUID> {
    /** Oldest unpublished rows, locked for the relay transaction; rows locked by another relay run are skipped. */
    @Query(value = "SELECT * FROM shipment_outbox WHERE published_at IS NULL ORDER BY created_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ShipmentOutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
