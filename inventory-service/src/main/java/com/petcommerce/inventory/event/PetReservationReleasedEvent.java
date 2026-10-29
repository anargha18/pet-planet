package com.petcommerce.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record PetReservationReleasedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID reservationId,
        UUID petId
) {
}
