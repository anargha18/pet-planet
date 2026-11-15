package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedDeliveryStatus;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import com.petcommerce.fulfillment.domain.model.TrackedPaymentStatus;
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
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the order-tracking projection (DEC-2, R-SVC-01) from order, inventory, payment, shipping, delivery and
 * notification events. Each event, in one transaction: ensures the order's row exists, locks it, records a timeline
 * entry keyed by the event ID (a duplicate stops here), then applies its monotonic change. Emits nothing.
 */
@Service
public class OrderTrackingProjector {

    private static final Logger log = LoggerFactory.getLogger(OrderTrackingProjector.class);

    private final OrderTrackingRepository trackingRepository;
    private final OrderTrackingEventRepository timelineRepository;

    public OrderTrackingProjector(OrderTrackingRepository trackingRepository, OrderTrackingEventRepository timelineRepository) {
        this.trackingRepository = trackingRepository;
        this.timelineRepository = timelineRepository;
    }

    @Transactional
    public boolean onOrderCreated(OrderCreatedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "OrderCreated", "order-service",
                "Order placed" + amount(" (total ", e.totalAmount(), ")"), t -> {
                    t.knowCustomer(e.customerId());
                    t.knowTotal(e.totalAmount());
                    t.applyOrderStatus(TrackedOrderStatus.CREATED, at(e.timestamp()));
                });
    }

    @Transactional
    public boolean onPetReserved(PetReservedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "PetReserved", "inventory-service",
                "Pets reserved; simulated payment started", t -> {
                    t.knowCustomer(e.customerId());
                    t.knowTotal(e.totalAmount());
                    t.applyOrderStatus(TrackedOrderStatus.PAYMENT_PENDING, at(e.timestamp()));
                    t.applyPaymentStatus(TrackedPaymentStatus.PENDING);
                });
    }

    @Transactional
    public boolean onPetReservationFailed(PetReservationFailedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "PetReservationFailed", "inventory-service",
                "Reservation failed: " + text(e.reason()), t -> { });
    }

    @Transactional
    public boolean onPaymentSucceeded(PaymentSucceededEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "PaymentSucceeded", "payment-service",
                "Payment" + amount(" of ", e.amount(), "") + " succeeded (simulated)", t -> {
                    t.knowCustomer(e.customerId());
                    t.applyPaymentStatus(TrackedPaymentStatus.SUCCEEDED);
                });
    }

    @Transactional
    public boolean onPaymentFailed(PaymentFailedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "PaymentFailed", "payment-service",
                "Payment failed: " + text(e.reason()), t -> {
                    t.knowCustomer(e.customerId());
                    t.applyPaymentStatus(TrackedPaymentStatus.FAILED);
                });
    }

    @Transactional
    public boolean onPaymentRefunded(PaymentRefundedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "PaymentRefunded", "payment-service",
                "Payment" + amount(" of ", e.amount(), "") + " refunded (" + text(e.reason()) + ")", t -> {
                    t.knowCustomer(e.customerId());
                    t.applyPaymentStatus(TrackedPaymentStatus.REFUNDED);
                });
    }

    @Transactional
    public boolean onOrderConfirmed(OrderConfirmedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "OrderConfirmed", "order-service",
                "Order confirmed", t -> {
                    t.knowCustomer(e.customerId());
                    t.knowTotal(e.totalAmount());
                    t.applyOrderStatus(TrackedOrderStatus.CONFIRMED, at(e.timestamp()));
                });
    }

    @Transactional
    public boolean onOrderCancelled(OrderCancelledEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "OrderCancelled", "order-service",
                "Order cancelled (" + text(e.reason()) + ")", t -> {
                    t.knowCustomer(e.customerId());
                    t.applyOrderStatus(TrackedOrderStatus.CANCELLED, at(e.timestamp()));
                });
    }

    @Transactional
    public boolean onOrderFailed(OrderFailedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "OrderFailed", "order-service",
                "Order failed (" + text(e.reason()) + ")", t -> {
                    t.knowCustomer(e.customerId());
                    t.applyOrderStatus(TrackedOrderStatus.FAILED, at(e.timestamp()));
                });
    }

    @Transactional
    public boolean onShipmentCreated(ShipmentCreatedEvent e) {
        return apply(e.eventId(), e.orderId(), e.timestamp(), "ShipmentCreated", "shipping-service",
                "Shipment created, tracking number " + text(e.trackingNumber()), t -> {
                    t.knowCustomer(e.customerId());
                    t.applyShipment(e.trackingNumber());
                });
    }

    @Transactional
    public boolean onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent e) {
        String note = e.note() == null || e.note().isBlank() ? "" : " (" + e.note().strip() + ")";
        return apply(e.eventId(), e.orderId(), e.timestamp(), "DeliveryStatusUpdated", "delivery-tracking-service",
                "Delivery status " + text(e.status()) + note, t -> {
                    t.knowCustomer(e.customerId());
                    t.applyDelivery(TrackedDeliveryStatus.parse(e.status()), e.trackingNumber());
                });
    }

    @Transactional
    public boolean onNotificationSent(NotificationSentEvent e) {
        String type = e.notificationType() == null ? "" : " [" + e.notificationType() + "]";
        return apply(e.eventId(), e.orderId(), e.timestamp(), "NotificationSent", "notification-service",
                "Customer notified" + type + ": " + text(e.message()), t -> t.knowCustomer(e.customerId()));
    }

    /** Returns false for events that were skipped (no IDs) or already projected. */
    private boolean apply(UUID eventId, Long orderId, Instant timestamp, String type, String source, String description,
                          Consumer<OrderTracking> change) {
        if (eventId == null || orderId == null) {
            log.warn("Ignoring {} without eventId/orderId", type);
            return false;
        }
        trackingRepository.insertIfAbsent(orderId);
        OrderTracking tracking = trackingRepository.lockByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("order_tracking row for order " + orderId + " vanished"));
        if (timelineRepository.insertIfAbsent(eventId, orderId, type, source, description, at(timestamp)) == 0) {
            log.info("Event {} ({}) already projected for order {}", eventId, type, orderId);
            return false;
        }
        change.accept(tracking);
        tracking.touch(Instant.now());
        trackingRepository.save(tracking);
        return true;
    }

    private static Instant at(Instant timestamp) {
        return timestamp != null ? timestamp : Instant.now();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String amount(String prefix, BigDecimal value, String suffix) {
        return value == null ? "" : prefix + value.setScale(2, RoundingMode.HALF_UP) + suffix;
    }
}
