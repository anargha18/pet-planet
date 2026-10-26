package com.petcommerce.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.notification.entity.Notification;
import com.petcommerce.notification.entity.NotificationOutboxEvent;
import com.petcommerce.notification.entity.NotificationStatus;
import com.petcommerce.notification.entity.ProcessedEvent;
import com.petcommerce.notification.event.NotificationSentEvent;
import com.petcommerce.notification.event.OrderConfirmedEvent;
import com.petcommerce.notification.repository.NotificationOutboxRepository;
import com.petcommerce.notification.repository.NotificationRepository;
import com.petcommerce.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                              ProcessedEventRepository processedEventRepository,
                              NotificationOutboxRepository notificationOutboxRepository,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
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

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed OrderConfirmedEvent {}", event.eventId());
            return notificationRepository.findByOrderId(event.orderId()).orElse(null);
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate OrderConfirmedEvent {} detected and ignored", event.eventId());
            return notificationRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Notification existing = notificationRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Notification already exists for order {}", event.orderId());
            return existing;
        }

        Long customerId = 101L;
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
        publishAfterCommit(NOTIFICATION_SENT_TOPIC, event.orderId().toString(), sentEvent, notification.getId(), event.orderId(), event.correlationId());

        log.info("Notification sent for order {} with notification {}", event.orderId(), notification.getId());
        return notification;
    }

    private void publishAfterCommit(String topic, String key, Object event, UUID notificationId, Long orderId, UUID correlationId) {
        notificationOutboxRepository.save(new NotificationOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, event);
                }
            });
            return;
        }

        kafkaTemplate.send(topic, key, event);
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize notification event", ex);
        }
    }
}
