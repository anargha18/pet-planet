package com.petcommerce.payment.messaging.publisher;

import com.petcommerce.payment.persistence.entity.PaymentOutboxEvent;
import com.petcommerce.payment.persistence.repository.PaymentOutboxRepository;
import com.petcommerce.platform.outbox.OutboxMessage;
import com.petcommerce.platform.outbox.OutboxStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Exposes this service's outbox table to the platform outbox relay (platform-commons). */
@Component
public class PaymentOutboxStore implements OutboxStore {

    private final PaymentOutboxRepository outboxRepository;

    public PaymentOutboxStore(PaymentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
        return outboxRepository.lockUnpublishedBatch(limit);
    }

    @Override
    public void save(OutboxMessage message) {
        outboxRepository.save((PaymentOutboxEvent) message);
    }
}
