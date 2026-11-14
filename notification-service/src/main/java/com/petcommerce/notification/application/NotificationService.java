package com.petcommerce.notification.application;

import com.petcommerce.notification.domain.model.Notification;
import com.petcommerce.notification.domain.model.NotificationStatus;
import com.petcommerce.notification.messaging.event.NotificationSentEvent;
import com.petcommerce.notification.messaging.event.OrderConfirmedEvent;
import com.petcommerce.notification.messaging.publisher.NotificationEventPublisher;
import com.petcommerce.notification.persistence.repository.NotificationRepository;
import com.petcommerce.notification.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public Optional<Notification> getNotificationByOrderId(Long orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    public List<Notification> getNotificationsByCustomerId(Long customerId) {
        return notificationRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Notification handleOrderConfirmedEvent(OrderConfirmedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }

        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed OrderConfirmedEvent {}", event.eventId());
            return notificationRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Notification existing = notificationRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Notification already exists for order {}", event.orderId());
            return existing;
        }

        if (event.customerId() == null) {
            // Required field: fail so the record is retried and then dead-lettered (R-EVT-07), never guessed.
            throw new IllegalArgumentException("Event " + event.eventId() + " for order " + event.orderId() + " has no customerId");
        }
        Long customerId = event.customerId();
        String message = "Your pet order " + event.orderId() + " has been confirmed and is on the way!";
        Notification notification = new Notification(event.orderId(), customerId, message, NotificationStatus.PENDING);
        notificationRepository.save(notification);

        NotificationSentEvent sentEvent = new NotificationSentEvent(
                UUID.randomUUID(),
                "NotificationSentEvent",
                1,
                event.aggregateId(),
                Instant.now(),
                event.correlationId(),
                event.orderId(),
                notification.getId(),
                message
        );

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);
        eventPublisher.publish(NOTIFICATION_SENT_TOPIC, event.orderId().toString(), sentEvent, notification.getId(), event.orderId(), event.correlationId());

        log.info("Notification sent for order {} (customer {}) with notification {}", event.orderId(), customerId, notification.getId());
        return notification;
    }

}
