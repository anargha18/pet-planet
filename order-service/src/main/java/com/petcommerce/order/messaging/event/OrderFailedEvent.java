package com.petcommerce.order.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published when an order ends FAILED: reservation failed, payment failed or the sale conflicted. */
public record OrderFailedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        List<UUID> petIds,
        /** RESERVATION_FAILED:&lt;inventory reason&gt;, PAYMENT_FAILED:&lt;payment reason&gt; or SALE_CONFLICT. */
        String reason
) {
}
