package com.petcommerce.shipping.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** This service's copy of delivery-tracking's event (only the fields it reads; unknown fields are ignored). */
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
        String status
) {
}
