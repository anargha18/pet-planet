package com.petcommerce.inventory.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** From payment: the order was charged (consumed to complete the sale). */
public record PaymentSucceededEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID paymentId,
        BigDecimal amount
) {
}
