package com.petcommerce.delivery.messaging.publisher;

import com.petcommerce.delivery.persistence.entity.DeliveryOutboxEvent;
import com.petcommerce.delivery.persistence.repository.DeliveryOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class DeliveryOutboxStore implements OutboxStore {

    private final DeliveryOutboxRepository outboxRepository;

    public DeliveryOutboxStore(DeliveryOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((DeliveryOutboxEvent) message);
    }
}
