package com.petcommerce.notification.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Customer-facing text for each notification type (R-SAGA-09: say what actually happened, nothing more). */
public final class NotificationWording {

    private NotificationWording() {
    }

    public static String orderConfirmed(Long orderId) {
        return "Your order #" + orderId + " is confirmed and your payment was received. We will let you know when it ships.";
    }

    /** {@code reason} is order-service's {@code CUSTOMER_CANCELLED} or {@code RESERVATION_EXPIRED}. */
    public static String orderCancelled(Long orderId, String reason) {
        if ("RESERVATION_EXPIRED".equals(reason)) {
            return "Your order #" + orderId + " was cancelled because it could not be completed in time."
                    + " Any payment taken will be refunded.";
        }
        return "Your order #" + orderId + " has been cancelled as you requested. Any payment taken will be refunded.";
    }

    /** {@code reason} is order-service's {@code <CAUSE>:<detail>}, e.g. {@code PAYMENT_FAILED:Card declined}. */
    public static String orderFailed(Long orderId, String reason) {
        String cause = reason == null ? "" : reason.split(":", 2)[0];
        String why = switch (cause) {
            case "PAYMENT_FAILED" -> "your payment was declined";
            case "RESERVATION_FAILED" -> "a pet in it is no longer available";
            case "SALE_CONFLICT" -> "a pet in it was no longer reserved for you";
            default -> "of a problem while processing it";
        };
        return "We could not complete your order #" + orderId + " because " + why + "."
                + " If you were charged, the payment will be refunded.";
    }

    public static String paymentRefunded(Long orderId, BigDecimal amount) {
        String value = amount == null ? "Your payment" : "Your payment of " + amount.setScale(2, RoundingMode.HALF_UP);
        return value + " for order #" + orderId + " has been refunded.";
    }

    public static String shipped(Long orderId, String trackingNumber) {
        return "Your order #" + orderId + " has shipped. Tracking number: " + trackingNumber + ".";
    }

    public static String outForDelivery(Long orderId) {
        return "Your order #" + orderId + " is out for delivery today.";
    }

    public static String delivered(Long orderId) {
        return "Your order #" + orderId + " has been delivered. Enjoy your new companion!";
    }

    public static String deliveryFailed(Long orderId, String note) {
        String detail = note == null || note.isBlank() ? "" : " (" + note.strip() + ")";
        return "We could not deliver your order #" + orderId + detail + ". Our team will contact you to arrange delivery.";
    }
}
