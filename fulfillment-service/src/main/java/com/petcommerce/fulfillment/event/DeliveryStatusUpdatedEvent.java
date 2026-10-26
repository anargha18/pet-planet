package com.petcommerce.fulfillment.event;

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
        UUID deliveryId,
        String trackingNumber,
        String status
) {
}
