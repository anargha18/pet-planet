package com.petcommerce.notification.domain.exception;

/** No notification exists for the order (→ 404). */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long orderId) {
        super("No notification found for order " + orderId);
    }
}
