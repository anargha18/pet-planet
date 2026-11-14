package com.petcommerce.order.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** From inventory: payment succeeded but the order's pets were no longer reserved for it. */
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
