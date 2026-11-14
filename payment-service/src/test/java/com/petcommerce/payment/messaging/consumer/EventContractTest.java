package com.petcommerce.payment.messaging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.payment.messaging.event.OrderCancelledEvent;
import com.petcommerce.payment.messaging.event.OrderFailedEvent;
import com.petcommerce.payment.messaging.event.PetReservedEvent;
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
    void consumesPetReservedAsProduced() throws Exception {
        PetReservedEvent event = read("pet-reserved-events", PetReservedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.reservationId(), "reservationId");
        assertNotNull(event.petId(), "petId");
        assertNotNull(event.totalAmount(), "totalAmount");
        assertNotNull(event.correlationId(), "correlationId");
        assertNotNull(event.customerId(), "customerId");
    }

    @Test
    void consumesOrderFailedAsProduced() throws Exception {
        OrderFailedEvent event = read("order-failed-events", OrderFailedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.reason(), "reason");
    }

    @Test
    void consumesOrderCancelledAsProduced() throws Exception {
        OrderCancelledEvent event = read("order-cancelled-events", OrderCancelledEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.reason(), "reason");
    }

    private static <T> T read(String topic, Class<T> type) throws Exception {
        String json = new ClassPathResource("contracts/" + topic + ".json").getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, type);
    }
}
