package com.petcommerce.platform.outbox;

import java.util.List;

/**
 * Port a service implements over its own outbox table. The relay calls both methods inside one transaction.
 */
public interface OutboxStore {

    /**
     * Returns up to {@code limit} unpublished rows, oldest first, locked for this transaction
     * ({@code SELECT ... FOR UPDATE SKIP LOCKED}) so two relay runs never send the same row concurrently.
     */
    List<? extends OutboxMessage> lockUnpublishedBatch(int limit);

    void save(OutboxMessage message);
}
