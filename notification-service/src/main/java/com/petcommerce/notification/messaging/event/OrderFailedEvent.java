package com.petcommerce.notification.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** This service's copy of order-service's event (only the fields it reads; unknown fields are ignored). */
public record OrderFailedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        String reason
) {
}
