package com.petcommerce.fulfillment.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** This service's copy of the producer's event: only the fields the tracking projection reads (unknown fields are ignored). */
public record DeliveryStatusUpdatedEvent(
        UUID eventId,
        String eventType,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        String trackingNumber,
        String status,
        String note
) {
}
