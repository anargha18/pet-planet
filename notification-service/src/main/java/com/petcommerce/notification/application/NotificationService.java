package com.petcommerce.notification.application;

import com.petcommerce.notification.api.dto.NotificationResponse;
import com.petcommerce.notification.api.mapper.NotificationMapper;
import com.petcommerce.notification.domain.model.Notification;
import com.petcommerce.notification.domain.model.NotificationStatus;
import com.petcommerce.notification.domain.model.NotificationType;
import com.petcommerce.notification.domain.policy.NotificationWording;
import com.petcommerce.notification.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.notification.messaging.event.NotificationSentEvent;
import com.petcommerce.notification.messaging.event.OrderCancelledEvent;
import com.petcommerce.notification.messaging.event.OrderConfirmedEvent;
import com.petcommerce.notification.messaging.event.OrderFailedEvent;
import com.petcommerce.notification.messaging.event.PaymentRefundedEvent;
import com.petcommerce.notification.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.notification.messaging.publisher.NotificationEventPublisher;
import com.petcommerce.notification.persistence.repository.NotificationRepository;
import com.petcommerce.notification.persistence.repository.ProcessedEventRepository;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Typed customer notifications (R-SAGA-09): one per order and {@link NotificationType}, created from saga and
 * delivery events, each announced with a {@code NotificationSentEvent} through the outbox.
 * Sending is simulated: a notification is stored and marked SENT in the same transaction.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final String NOTIFICATION_SENT_TOPIC = "notification-sent-events";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               ProcessedEventRepository processedEventRepository,
                               NotificationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---- queries (R-SEC-04: owner or admin) ----

    /** All notifications of one order, oldest first. Empty if none yet; 403 if they belong to someone else. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getForOrder(Long orderId, AuthenticatedCaller caller) {
        List<Notification> notifications = notificationRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        notifications.forEach(n -> caller.requireAccessTo(n.getCustomerId()));
        return notifications.stream().map(NotificationMapper::toResponse).toList();
    }

    /** All notifications of a customer, newest first: users may only ask for their own, admins for anyone. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getForCustomer(Long customerId, AuthenticatedCaller caller) {
        caller.requireAccessTo(customerId);
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(NotificationMapper::toResponse).toList();
    }

    // ---- event handlers ----

    @Transactional
    public Notification handleOrderConfirmed(OrderConfirmedEvent e) {
        return notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(), NotificationType.ORDER_CONFIRMED,
                NotificationWording.orderConfirmed(e.orderId()));
    }

    @Transactional
    public Notification handleOrderCancelled(OrderCancelledEvent e) {
        return notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(), NotificationType.ORDER_CANCELLED,
                NotificationWording.orderCancelled(e.orderId(), e.reason()));
    }

    @Transactional
    public Notification handleOrderFailed(OrderFailedEvent e) {
        return notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(), NotificationType.ORDER_FAILED,
                NotificationWording.orderFailed(e.orderId(), e.reason()));
    }

    @Transactional
    public Notification handlePaymentRefunded(PaymentRefundedEvent e) {
        return notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(), NotificationType.PAYMENT_REFUNDED,
                NotificationWording.paymentRefunded(e.orderId(), e.amount()));
    }

    @Transactional
    public Notification handleShipmentCreated(ShipmentCreatedEvent e) {
        return notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(), NotificationType.SHIPPED,
                NotificationWording.shipped(e.orderId(), e.trackingNumber()));
    }

    /** IN_TRANSIT is already announced as SHIPPED (on ShipmentCreated); the later carrier statuses get their own. */
    @Transactional
    public Notification handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent e) {
        if (e.status() == null) {
            return null;
        }
        return switch (e.status()) {
            case "OUT_FOR_DELIVERY" -> notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(),
                    NotificationType.OUT_FOR_DELIVERY, NotificationWording.outForDelivery(e.orderId()));
            case "DELIVERED" -> notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(),
                    NotificationType.DELIVERED, NotificationWording.delivered(e.orderId()));
            case "DELIVERY_FAILED", "FAILED" -> notify(e.eventId(), e.correlationId(), e.orderId(), e.customerId(),
                    NotificationType.DELIVERY_FAILED, NotificationWording.deliveryFailed(e.orderId(), e.note()));
            default -> null;
        };
    }

    /**
     * Idempotent (processed_events, plus at most one notification per order and type). A missing customerId is taken
     * from an earlier notification of the same order; if there is none the event fails and is dead-lettered
     * (R-EVT-07: required fields are never guessed).
     */
    private Notification notify(UUID eventId, UUID correlationId, Long orderId, Long customerId,
                                NotificationType type, String message) {
        if (eventId == null || orderId == null) {
            log.warn("Ignoring {} event without eventId/orderId", type);
            return null;
        }
        if (processedEventRepository.markProcessed(eventId) == 0) {
            log.info("Skipping already processed event {} ({})", eventId, type);
            return null;
        }
        if (notificationRepository.existsByOrderIdAndType(orderId, type)) {
            log.info("Order {} already has a {} notification", orderId, type);
            return null;
        }
        Long owner = customerId != null ? customerId
                : notificationRepository.findFirstByOrderId(orderId).map(Notification::getCustomerId).orElse(null);
        if (owner == null) {
            throw new IllegalArgumentException("Event " + eventId + " (" + type + ") for order " + orderId + " has no customerId");
        }

        Notification notification = new Notification(orderId, owner, type, message, NotificationStatus.PENDING);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notification = notificationRepository.save(notification);

        NotificationSentEvent sentEvent = new NotificationSentEvent(
                UUID.randomUUID(),
                "NotificationSentEvent",
                1,
                notification.getId(),
                Instant.now(),
                correlationId,
                orderId,
                notification.getId(),
                message,
                owner,
                type.name()
        );
        eventPublisher.publish(NOTIFICATION_SENT_TOPIC, orderId.toString(), sentEvent, notification.getId(), orderId, correlationId);
        log.info("Sent {} notification {} for order {} (customer {})", type, notification.getId(), orderId, owner);
        return notification;
    }
}
