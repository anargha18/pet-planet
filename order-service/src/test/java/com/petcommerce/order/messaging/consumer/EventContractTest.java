package com.petcommerce.order.messaging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.order.messaging.event.InventorySaleConflictEvent;
import com.petcommerce.order.messaging.event.PetReservationFailedEvent;
import com.petcommerce.order.messaging.event.PetReservedEvent;
import com.petcommerce.order.messaging.event.PetsSoldEvent;
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
    }

    @Test
    void consumesPetsSoldAsProduced() throws Exception {
        PetsSoldEvent event = read("pets-sold-events", PetsSoldEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.correlationId(), "correlationId");
    }

    @Test
    void consumesInventorySaleConflictAsProduced() throws Exception {
        InventorySaleConflictEvent event = read("inventory-sale-conflict-events", InventorySaleConflictEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.reason(), "reason");
    }

    @Test
    void consumesPetReservationFailedAsProduced() throws Exception {
        PetReservationFailedEvent event = read("pet-reservation-failed-events", PetReservationFailedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.reason(), "reason");
        assertNotNull(event.correlationId(), "correlationId");
    }

    private static <T> T read(String topic, Class<T> type) throws Exception {
        String json = new ClassPathResource("contracts/" + topic + ".json").getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, type);
    }
}
