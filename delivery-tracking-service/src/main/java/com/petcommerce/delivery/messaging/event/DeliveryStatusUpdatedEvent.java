package com.petcommerce.delivery.messaging.event;

import java.time.Instant;
import java.util.UUID;

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
