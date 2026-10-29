package com.petcommerce.order.event;

import java.time.Instant;
import java.util.UUID;

public record PetReservationFailedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID petId,
        String reason
) {
}

