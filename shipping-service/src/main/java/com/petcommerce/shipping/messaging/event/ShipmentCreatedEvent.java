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
}
