package com.petcommerce.order.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** From inventory: every pet reserved for the order is now SOLD (consumed to confirm the order). */
public record PetsSoldEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        List<UUID> petIds
) {
}
