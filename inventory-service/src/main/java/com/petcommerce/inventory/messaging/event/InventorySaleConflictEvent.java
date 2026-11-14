package com.petcommerce.inventory.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** Published when payment succeeded but the pets of the order were no longer reserved for it. */
public record InventorySaleConflictEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        String reason
) {
}
