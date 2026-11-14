package com.petcommerce.inventory.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** From order: the order failed; its reservations are released. */
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
