package com.petcommerce.platform.outbox;

import com.petcommerce.platform.kafka.EventHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka (R-EVT-03).
 * <ul>
 *   <li>A row is marked published only after the broker acknowledged it (blocking send with timeout).</li>
 *   <li>On failure the row records the error and the batch stops, preserving event order; the next run retries.</li>
 *   <li>The payload is sent as the stored JSON string with no Java type headers (R-EVT-01).</li>
 * </ul>
 * Must run inside a transaction so the {@link OutboxStore#lockUnpublishedBatch} row locks hold until commit.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(KafkaTemplate<String, String> kafkaTemplate, int batchSize, Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    /** Relays one batch; returns how many rows were published. */
    public int relayBatch(OutboxStore store) {
        List<? extends OutboxMessage> batch = store.lockUnpublishedBatch(batchSize);
        int published = 0;
        for (OutboxMessage message : batch) {
            try {
                kafkaTemplate.send(toRecord(message)).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                message.markPublished(Instant.now());
                store.save(message);
                published++;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                recordFailure(store, message, ex);
                break;
            } catch (Exception ex) {
                recordFailure(store, message, ex);
                break;
            }
        }
        return published;
    }

    static ProducerRecord<String, String> toRecord(OutboxMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
        addHeader(record, EventHeaders.EVENT_ID, message.getEventId());
        addHeader(record, EventHeaders.EVENT_TYPE, message.getEventType());
        addHeader(record, EventHeaders.CORRELATION_ID, message.getCorrelationId());
        return record;
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, Object value) {
        if (value != null) {
            record.headers().add(name, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void recordFailure(OutboxStore store, OutboxMessage message, Exception ex) {
        String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        log.warn("Outbox event {} ({}) not published yet, will retry: {}", message.getEventId(), message.getEventType(), error);
        message.recordFailure(error);
        store.save(message);
    }
}
