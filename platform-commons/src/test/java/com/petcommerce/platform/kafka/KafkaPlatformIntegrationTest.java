package com.petcommerce.platform.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-broker test of the shared Kafka conventions (R-EVT-01, R-EVT-04): typed consumption without type headers,
 * tolerance of stale producer type headers, and poison messages routed to the DLT without blocking the consumer.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = KafkaPlatformIntegrationTest.TestApp.class, properties = {
        "spring.kafka.consumer.group-id=platform-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "platform.kafka.retry.interval-ms=100",
        "platform.kafka.retry.max-retries=1"
})
class KafkaPlatformIntegrationTest {

    static final String TOPIC = "platform-it-orders";

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestListener listener;

    @Test
    void typedConsumptionWithoutTypeHeadersAndPoisonMessagesGoToDlt() throws Exception {
        UUID orderId = UUID.randomUUID();

        // 1) Valid JSON carrying a stale Java type header from another service (what the old producers sent).
        ProducerRecord<String, String> legacy = new ProducerRecord<>(TOPIC, "k1",
                "{\"orderId\":\"" + orderId + "\",\"amount\":12.50,\"occurredAt\":\"2026-11-14T10:00:00Z\",\"extraField\":true}");
        legacy.headers().add("__TypeId__", "com.petcommerce.other.service.SomeEvent".getBytes(StandardCharsets.UTF_8));
        legacy.headers().add(EventHeaders.CORRELATION_ID, "corr-1".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(legacy).get(10, TimeUnit.SECONDS);

        // 2) Unreadable JSON: must go to the DLT, not block the partition.
        kafkaTemplate.send(TOPIC, "k1", "{not json").get(10, TimeUnit.SECONDS);

        // 3) A valid message after the poison one must still be processed.
        UUID secondOrderId = UUID.randomUUID();
        kafkaTemplate.send(TOPIC, "k1", "{\"orderId\":\"" + secondOrderId + "\",\"amount\":1,\"occurredAt\":\"2026-11-14T10:00:01Z\"}")
                .get(10, TimeUnit.SECONDS);

        TestEvent first = listener.received.poll(30, TimeUnit.SECONDS);
        assertNotNull(first, "typed event received");
        assertEquals(orderId, first.orderId());
        assertEquals(0, new BigDecimal("12.50").compareTo(first.amount()));
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), first.occurredAt());
        assertEquals("corr-1", listener.correlationIds.poll(5, TimeUnit.SECONDS), "correlation header copied to MDC");

        TestEvent second = listener.received.poll(30, TimeUnit.SECONDS);
        assertNotNull(second, "consumer moved past the poison message");
        assertEquals(secondOrderId, second.orderId());

        List<String> dltValues = readAll(TOPIC + ".DLT", Duration.ofSeconds(30), 1);
        assertEquals(List.of("{not json"), dltValues);
    }

    private static List<String> readAll(String topic, Duration timeout, int expected) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new java.util.ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (values.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(record.value());
                }
            }
        }
        assertTrue(values.size() >= expected, "expected " + expected + " DLT records from " + topic);
        return values;
    }

    record TestEvent(UUID orderId, BigDecimal amount, Instant occurredAt) {
    }

    static class TestListener {
        final BlockingQueue<TestEvent> received = new LinkedBlockingQueue<>();
        final BlockingQueue<String> correlationIds = new LinkedBlockingQueue<>();

        @KafkaListener(topics = TOPIC)
        void onEvent(TestEvent event) {
            String correlationId = org.slf4j.MDC.get(EventHeaders.CORRELATION_ID);
            if (correlationId != null) {
                correlationIds.add(correlationId);
            }
            received.add(event);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class TestApp {
        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }
}
