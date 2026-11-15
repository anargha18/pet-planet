package com.petcommerce.delivery.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on every delivery status change. {@code previousStatus} (null for the first, IN_TRANSIT event) and
 * {@code note} were added in Phase 4; both are additive, so older consumers ignore them.
 */
public record DeliveryStatusUpdatedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        Long customerId,
        UUID deliveryId,
        String trackingNumber,
        String status,
        String previousStatus,
        String note
) {
}
