package com.petcommerce.notification.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** This service's copy of delivery-tracking's event. */
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
        String note
) {
}
