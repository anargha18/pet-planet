package com.petcommerce.fulfillment.event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryStatusUpdatedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        UUID deliveryId,
        String trackingNumber,
        String status
) {
    public DeliveryStatusUpdatedEvent(
            UUID eventId,
            String eventType,
            Integer eventVersion,
            UUID aggregateId,
            Instant timestamp,
            UUID correlationId,
            Long orderId,
            UUID deliveryId,
            String trackingNumber,
            String status
    ) {
        this(eventId, eventType, eventVersion, aggregateId, timestamp, correlationId, orderId, 101L, deliveryId, trackingNumber, status);
    }
}
