package com.petcommerce.notification.domain.model;

/**
 * What a notification is about (R-SAGA-09). At most one notification per order and type. The plan's six types plus
 * {@code OUT_FOR_DELIVERY} and {@code DELIVERY_FAILED}, which delivery-tracking can now report (R-SAGA-06).
 */
public enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_FAILED,
    PAYMENT_REFUNDED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED
}
