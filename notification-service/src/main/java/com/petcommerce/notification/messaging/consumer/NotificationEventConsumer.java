package com.petcommerce.notification.messaging.consumer;

import com.petcommerce.notification.application.NotificationService;
import com.petcommerce.notification.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.notification.messaging.event.OrderCancelledEvent;
import com.petcommerce.notification.messaging.event.OrderConfirmedEvent;
import com.petcommerce.notification.messaging.event.OrderFailedEvent;
import com.petcommerce.notification.messaging.event.PaymentRefundedEvent;
import com.petcommerce.notification.messaging.event.ShipmentCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** One listener per event type that the customer should hear about (R-SAGA-09). */
@Component
public class NotificationEventConsumer {

    private static final String GROUP = "notification-service";

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order-confirmed-events", groupId = GROUP)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.handleOrderConfirmed(event);
    }

    @KafkaListener(topics = "order-cancelled-events", groupId = GROUP)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.handleOrderCancelled(event);
    }

    @KafkaListener(topics = "order-failed-events", groupId = GROUP)
    public void onOrderFailed(OrderFailedEvent event) {
        notificationService.handleOrderFailed(event);
    }

    @KafkaListener(topics = "payment-refunded-events", groupId = GROUP)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        notificationService.handlePaymentRefunded(event);
    }

    @KafkaListener(topics = "shipment-created-events", groupId = GROUP)
    public void onShipmentCreated(ShipmentCreatedEvent event) {
        notificationService.handleShipmentCreated(event);
    }

    @KafkaListener(topics = "delivery-status-updated-events", groupId = GROUP)
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        notificationService.handleDeliveryStatusUpdated(event);
    }
}
