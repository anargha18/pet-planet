package com.petcommerce.inventory.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published when every pet reserved for an order is marked SOLD; the order is confirmed on this event. */
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
