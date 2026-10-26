package com.petcommerce.notification.event;

import java.time.Instant;
import java.util.UUID;

public record NotificationSentEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID notificationId,
        String message
) {
}
