package com.petcommerce.shipping.event;

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
        UUID shipmentId,
        String trackingNumber,
        String status
) {
}
