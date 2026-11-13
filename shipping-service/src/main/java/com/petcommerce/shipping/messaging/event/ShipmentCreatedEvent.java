package com.petcommerce.shipping.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ShipmentCreatedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        UUID shipmentId,
        String trackingNumber,
        String status
) {
    public ShipmentCreatedEvent(
            UUID eventId,
            String eventType,
            Integer eventVersion,
            UUID aggregateId,
            Instant timestamp,
            UUID correlationId,
            Long orderId,
            UUID shipmentId,
            String trackingNumber,
            String status
    ) {
        this(eventId, eventType, eventVersion, aggregateId, timestamp, correlationId, orderId, 101L, shipmentId, trackingNumber, status);
    }
}
