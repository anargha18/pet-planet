package com.petcommerce.inventory.messaging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.inventory.messaging.event.OrderConfirmedEvent;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Consumer contract test (R-TEST-01, part): each fixture in src/test/resources/contracts is the exact JSON the
 * producing service wrote to Kafka (captured from the running stack on 2026-11-14). It must keep deserializing
 * into this service's own event record with the same Jackson settings Spring Boot uses at runtime.
 * If a producer renames or retypes a field, this test fails.
 */
class EventContractTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void consumesOrderCreatedAsProduced() throws Exception {
        OrderCreatedEvent event = read("order-created-events", OrderCreatedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.totalAmount(), "totalAmount");
        assertNotNull(event.correlationId(), "correlationId");
    }

    @Test
    void consumesOrderConfirmedAsProduced() throws Exception {
        OrderConfirmedEvent event = read("order-confirmed-events", OrderConfirmedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.totalAmount(), "totalAmount");
        assertNotNull(event.correlationId(), "correlationId");
    }

    private static <T> T read(String topic, Class<T> type) throws Exception {
        String json = new ClassPathResource("contracts/" + topic + ".json").getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, type);
    }
}
