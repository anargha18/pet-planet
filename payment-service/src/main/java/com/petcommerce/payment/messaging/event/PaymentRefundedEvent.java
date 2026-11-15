package com.petcommerce.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when a successful charge is refunded because the order was cancelled or failed. */
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
        /** Owner of the order (additive, Phase 4); null for payments created before payment V4. */
        Long customerId
) {
}
