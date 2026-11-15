package com.petcommerce.notification.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** This service's copy of payment-service's event; {@code customerId} may be null for old payments. */
public record PaymentRefundedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID paymentId,
        BigDecimal amount,
        String reason,
        Long customerId
) {
}
