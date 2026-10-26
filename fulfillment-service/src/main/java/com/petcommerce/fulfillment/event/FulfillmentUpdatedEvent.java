package com.petcommerce.fulfillment.event;

import java.time.Instant;
import java.util.UUID;

public record FulfillmentUpdatedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID fulfillmentId,
        String trackingNumber,
        String status,
        String notes
) {
}
