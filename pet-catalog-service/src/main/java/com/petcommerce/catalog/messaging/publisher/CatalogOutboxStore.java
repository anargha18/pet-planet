package com.petcommerce.catalog.messaging.publisher;

import com.petcommerce.catalog.persistence.entity.CatalogOutboxEvent;
import com.petcommerce.catalog.persistence.repository.CatalogOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class CatalogOutboxStore implements OutboxStore {

    private final CatalogOutboxRepository outboxRepository;

    public CatalogOutboxStore(CatalogOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((CatalogOutboxEvent) message);
    }
}
