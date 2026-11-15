package com.petcommerce.inventory.messaging.publisher;

import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import com.petcommerce.inventory.persistence.repository.InventoryOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class InventoryOutboxStore implements OutboxStore {

    private final InventoryOutboxRepository outboxRepository;

    public InventoryOutboxStore(InventoryOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((InventoryOutboxEvent) message);
    }
}
