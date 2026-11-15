package com.petcommerce.notification.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** This service's copy of shipping-service's event. */
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
}
