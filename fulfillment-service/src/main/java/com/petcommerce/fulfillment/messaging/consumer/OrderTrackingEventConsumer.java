package com.petcommerce.fulfillment.messaging.consumer;

import com.petcommerce.fulfillment.application.OrderTrackingProjector;
import com.petcommerce.fulfillment.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.messaging.event.NotificationSentEvent;
import com.petcommerce.fulfillment.messaging.event.OrderCancelledEvent;
import com.petcommerce.fulfillment.messaging.event.OrderConfirmedEvent;
import com.petcommerce.fulfillment.messaging.event.OrderCreatedEvent;
import com.petcommerce.fulfillment.messaging.event.OrderFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentRefundedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentSucceededEvent;
import com.petcommerce.fulfillment.messaging.event.PetReservationFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PetReservedEvent;
import com.petcommerce.fulfillment.messaging.event.ShipmentCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the tracking projection. The consumer group {@value #GROUP} is new in Phase 4: with
 * {@code auto-offset-reset: earliest} it replays every retained message once, so the projection is rebuilt from
 * history. Changing the group ID (and emptying the tables) rebuilds it again.
 */
@Component
public class OrderTrackingEventConsumer {

    static final String GROUP = "fulfillment-tracking-projection";

    private final OrderTrackingProjector projector;

    public OrderTrackingEventConsumer(OrderTrackingProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(topics = "order-created-events", groupId = GROUP)
    public void onOrderCreated(OrderCreatedEvent event) {
        projector.onOrderCreated(event);
    }

    @KafkaListener(topics = "pet-reserved-events", groupId = GROUP)
    public void onPetReserved(PetReservedEvent event) {
        projector.onPetReserved(event);
    }

    @KafkaListener(topics = "pet-reservation-failed-events", groupId = GROUP)
    public void onPetReservationFailed(PetReservationFailedEvent event) {
        projector.onPetReservationFailed(event);
    }

    @KafkaListener(topics = "payment-succeeded-events", groupId = GROUP)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        projector.onPaymentSucceeded(event);
    }

    @KafkaListener(topics = "payment-failed-events", groupId = GROUP)
    public void onPaymentFailed(PaymentFailedEvent event) {
        projector.onPaymentFailed(event);
    }

    @KafkaListener(topics = "payment-refunded-events", groupId = GROUP)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        projector.onPaymentRefunded(event);
    }

    @KafkaListener(topics = "order-confirmed-events", groupId = GROUP)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        projector.onOrderConfirmed(event);
    }

    @KafkaListener(topics = "order-cancelled-events", groupId = GROUP)
    public void onOrderCancelled(OrderCancelledEvent event) {
        projector.onOrderCancelled(event);
    }

    @KafkaListener(topics = "order-failed-events", groupId = GROUP)
    public void onOrderFailed(OrderFailedEvent event) {
        projector.onOrderFailed(event);
    }

    @KafkaListener(topics = "shipment-created-events", groupId = GROUP)
    public void onShipmentCreated(ShipmentCreatedEvent event) {
        projector.onShipmentCreated(event);
    }

    @KafkaListener(topics = "delivery-status-updated-events", groupId = GROUP)
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        projector.onDeliveryStatusUpdated(event);
    }

    @KafkaListener(topics = "notification-sent-events", groupId = GROUP)
    public void onNotificationSent(NotificationSentEvent event) {
        projector.onNotificationSent(event);
    }
}
