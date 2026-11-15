package com.petcommerce.notification.api.dto;

import com.petcommerce.notification.domain.model.NotificationStatus;
import com.petcommerce.notification.domain.model.NotificationType;
import java.time.Instant;
import java.util.UUID;

/** HTTP view of a notification (R-SEC-06: controllers never return JPA entities). */
public record NotificationResponse(
        UUID id,
        Long orderId,
        Long customerId,
        NotificationType type,
        String message,
        NotificationStatus status,
        Instant createdAt,
        Instant sentAt
) {
}
