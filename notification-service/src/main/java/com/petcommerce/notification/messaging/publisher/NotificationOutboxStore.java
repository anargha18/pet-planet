package com.petcommerce.notification.messaging.publisher;

import com.petcommerce.notification.persistence.entity.NotificationOutboxEvent;
import com.petcommerce.notification.persistence.repository.NotificationOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class NotificationOutboxStore implements OutboxStore {

    private final NotificationOutboxRepository outboxRepository;

    public NotificationOutboxStore(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((NotificationOutboxEvent) message);
    }
}
