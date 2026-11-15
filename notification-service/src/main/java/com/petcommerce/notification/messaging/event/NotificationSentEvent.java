package com.petcommerce.notification.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** Published for every notification sent. {@code customerId} and {@code notificationType} were added in Phase 4. */
public record NotificationSentEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant timestamp,
        UUID correlationId,
        Long orderId,
        UUID notificationId,
        String message,
        Long customerId,
        String notificationType
) {
}
