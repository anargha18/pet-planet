package com.petcommerce.notification.messaging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.notification.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.notification.messaging.event.OrderCancelledEvent;
import com.petcommerce.notification.messaging.event.OrderConfirmedEvent;
import com.petcommerce.notification.messaging.event.OrderFailedEvent;
import com.petcommerce.notification.messaging.event.PaymentRefundedEvent;
import com.petcommerce.notification.messaging.event.ShipmentCreatedEvent;
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
    void consumesOrderConfirmedAsProduced() throws Exception {
        OrderConfirmedEvent event = read("order-confirmed-events", OrderConfirmedEvent.class);

        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.petIds(), "petIds");
        assertNotNull(event.totalAmount(), "totalAmount");
        assertNotNull(event.correlationId(), "correlationId");
    }

    @Test
    void consumesOrderCancelledAsProduced() throws Exception {
        OrderCancelledEvent event = read("order-cancelled-events", OrderCancelledEvent.class);
        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.reason(), "reason");
    }

    @Test
    void consumesOrderFailedAsProduced() throws Exception {
        OrderFailedEvent event = read("order-failed-events", OrderFailedEvent.class);
        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.reason(), "reason");
    }

    @Test
    void consumesShipmentCreatedAsProduced() throws Exception {
        ShipmentCreatedEvent event = read("shipment-created-events", ShipmentCreatedEvent.class);
        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.trackingNumber(), "trackingNumber");
    }

    @Test
    void consumesDeliveryStatusUpdatedAsProduced() throws Exception {
        DeliveryStatusUpdatedEvent event = read("delivery-status-updated-events", DeliveryStatusUpdatedEvent.class);
        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.customerId(), "customerId");
        assertNotNull(event.status(), "status");
    }

    @Test
    void consumesPaymentRefundedAsProduced() throws Exception {
        PaymentRefundedEvent event = read("payment-refunded-events", PaymentRefundedEvent.class);
        assertNotNull(event.eventId(), "eventId");
        assertNotNull(event.orderId(), "orderId");
        assertNotNull(event.amount(), "amount");
        assertNotNull(event.customerId(), "customerId");
    }

    private static <T> T read(String topic, Class<T> type) throws Exception {
        String json = new ClassPathResource("contracts/" + topic + ".json").getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, type);
    }
}
