package com.petcommerce.payment.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** From order: reservation failed, payment failed or the sale conflicted. */
public record OrderFailedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        List<UUID> petIds,
        String reason
) {
}
