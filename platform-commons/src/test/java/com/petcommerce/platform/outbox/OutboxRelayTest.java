package com.petcommerce.platform.outbox;

import com.petcommerce.platform.kafka.EventHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(kafkaTemplate, 50, Duration.ofSeconds(1));

    @Test
    void publishesPayloadWithHeadersAndMarksRowPublished() {
        FakeMessage message = new FakeMessage("order-created-events", "42", "{\"orderId\":42}");
        FakeStore store = new FakeStore(List.of(message));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        int published = relay.relayBatch(store);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals(1, published);
        assertEquals("order-created-events", sent.topic());
        assertEquals("42", sent.key());
        assertEquals("{\"orderId\":42}", sent.value());
        assertEquals(message.eventId.toString(), header(sent, EventHeaders.EVENT_ID));
        assertEquals("TestEvent", header(sent, EventHeaders.EVENT_TYPE));
        assertEquals(message.correlationId.toString(), header(sent, EventHeaders.CORRELATION_ID));
        assertNull(sent.headers().lastHeader("__TypeId__"), "no Java type information on the wire");
        assertNotNull(message.publishedAt);
        assertEquals(List.of(message), store.saved);
    }

    @Test
    void failedSendRecordsErrorKeepsRowUnpublishedAndStopsBatchToPreserveOrder() {
        FakeMessage first = new FakeMessage("t", "1", "{}");
        FakeMessage second = new FakeMessage("t", "1", "{}");
        FakeStore store = new FakeStore(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.relayBatch(store);

        assertEquals(0, published);
        assertNull(first.publishedAt);
        assertEquals(1, first.attempts);
        assertEquals("ExecutionException: java.lang.IllegalStateException: broker down", first.lastError);
        assertEquals(0, second.attempts, "later rows are not attempted after a failure");
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    static final class FakeStore implements OutboxStore {
        private final List<FakeMessage> pending;
        final List<OutboxMessage> saved = new ArrayList<>();

        FakeStore(List<FakeMessage> pending) {
            this.pending = pending;
        }

        @Override
        public List<? extends OutboxMessage> lockUnpublishedBatch(int limit) {
            return pending;
        }

        @Override
        public void save(OutboxMessage message) {
            saved.add(message);
        }
    }

    static final class FakeMessage implements OutboxMessage {
        final UUID eventId = UUID.randomUUID();
        final UUID correlationId = UUID.randomUUID();
        final String topic;
        final String key;
        final String payload;
        Instant publishedAt;
        int attempts;
        String lastError;

        FakeMessage(String topic, String key, String payload) {
            this.topic = topic;
            this.key = key;
            this.payload = payload;
        }

        @Override public UUID getEventId() { return eventId; }
        @Override public String getTopic() { return topic; }
        @Override public String getEventType() { return "TestEvent"; }
        @Override public String getMessageKey() { return key; }
        @Override public String getPayload() { return payload; }
        @Override public UUID getCorrelationId() { return correlationId; }
        @Override public void markPublished(Instant at) { publishedAt = at; }
        @Override public void recordFailure(String error) { attempts++; lastError = error; }
    }
}
