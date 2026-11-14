package com.petcommerce.payment.persistence.repository;

import com.petcommerce.payment.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Atomically records that an event was handled (R-EVT-05).
     * Returns 1 the first time, 0 if the event was already processed; no exception-driven control flow.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int markProcessed(@Param("eventId") UUID eventId);
}
