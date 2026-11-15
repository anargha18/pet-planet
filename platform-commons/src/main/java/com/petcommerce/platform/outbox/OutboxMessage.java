package com.petcommerce.platform.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a service's transactional outbox, as seen by the {@link OutboxRelay}.
 * Each service maps its own outbox table (an entity in its persistence package) and implements this interface.
 */
public interface OutboxMessage {

    UUID getEventId();

    String getTopic();

    String getEventType();

    /** Kafka record key (e.g. order ID or pet ID) so related events stay in order on one partition. */
    String getMessageKey();

    /** The event as JSON, exactly as it will be written to Kafka. */
    String getPayload();

    /** May be null when the event has no correlation ID. */
    UUID getCorrelationId();

    void markPublished(Instant publishedAt);

    void recordFailure(String error);
}
