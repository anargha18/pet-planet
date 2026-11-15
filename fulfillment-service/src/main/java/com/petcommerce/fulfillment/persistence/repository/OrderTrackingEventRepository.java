package com.petcommerce.fulfillment.persistence.repository;

import com.petcommerce.fulfillment.domain.model.OrderTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderTrackingEventRepository extends JpaRepository<OrderTrackingEvent, UUID> {

    /** Adds a timeline entry; returns 0 if this event was already recorded (R-EVT-05 idempotency). */
    @Modifying
    @Query(value = "INSERT INTO order_tracking_events (event_id, order_id, event_type, source, description, occurred_at, recorded_at) "
            + "VALUES (:eventId, :orderId, :eventType, :source, :description, :occurredAt, now()) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("orderId") Long orderId, @Param("eventType") String eventType,
                       @Param("source") String source, @Param("description") String description,
                       @Param("occurredAt") Instant occurredAt);

    List<OrderTrackingEvent> findByOrderIdOrderByOccurredAtAscRecordedAtAsc(Long orderId);
}
