package com.petcommerce.shipping.messaging.publisher;

import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import com.petcommerce.shipping.persistence.entity.ShipmentOutboxEvent;
import com.petcommerce.shipping.persistence.repository.ShipmentOutboxRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class ShipmentOutboxStore implements OutboxStore {

    private final ShipmentOutboxRepository outboxRepository;

    public ShipmentOutboxStore(ShipmentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((ShipmentOutboxEvent) message);
    }
}
