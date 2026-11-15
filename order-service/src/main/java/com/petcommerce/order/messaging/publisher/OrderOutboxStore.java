package com.petcommerce.order.messaging.publisher;

import com.petcommerce.order.persistence.entity.OrderOutboxEvent;
import com.petcommerce.order.persistence.repository.OrderOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class OrderOutboxStore implements OutboxStore {

    private final OrderOutboxRepository outboxRepository;

    public OrderOutboxStore(OrderOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((OrderOutboxEvent) message);
    }
}
