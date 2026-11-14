package com.petcommerce.notification.api.mapper;

import com.petcommerce.notification.api.dto.NotificationResponse;
import com.petcommerce.notification.domain.model.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getOrderId(),
                n.getCustomerId(),
                n.getMessage(),
                n.getStatus(),
                n.getCreatedAt(),
                n.getSentAt()
        );
    }
}
