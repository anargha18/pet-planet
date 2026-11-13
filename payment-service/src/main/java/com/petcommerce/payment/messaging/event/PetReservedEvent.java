package com.petcommerce.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PetReservedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID reservationId,
        UUID petId,
        BigDecimal totalAmount
) {
    public PetReservedEvent(
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
        this(eventId, eventType, eventVersion, aggregateId, timestamp, correlationId, orderId, reservationId, petId, null);
    }
}
