package com.petcommerce.fulfillment.messaging.publisher;

import com.petcommerce.fulfillment.persistence.entity.FulfillmentOutboxEvent;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class FulfillmentOutboxStore implements OutboxStore {

    private final FulfillmentOutboxRepository outboxRepository;

    public FulfillmentOutboxStore(FulfillmentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((FulfillmentOutboxEvent) message);
    }
}
