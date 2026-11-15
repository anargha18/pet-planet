package com.petcommerce.fulfillment.messaging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.fulfillment.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.messaging.event.NotificationSentEvent;
import com.petcommerce.fulfillment.messaging.event.OrderCancelledEvent;
import com.petcommerce.fulfillment.messaging.event.OrderConfirmedEvent;
import com.petcommerce.fulfillment.messaging.event.OrderCreatedEvent;
import com.petcommerce.fulfillment.messaging.event.OrderFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentRefundedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentSucceededEvent;
import com.petcommerce.fulfillment.messaging.event.PetReservationFailedEvent;
import com.petcommerce.fulfillment.messaging.event.PetReservedEvent;
import com.petcommerce.fulfillment.messaging.event.ShipmentCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Consumer contract test (R-TEST-01, part): each fixture in src/test/resources/contracts is the exact JSON a producing
 * service wrote to Kafka (captured from the running stack). It must deserialize into this service's own record with
 * the runtime Jackson settings, keeping the fields the tracking projection needs.
 */
class EventContractTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void consumesOrderCreated() throws Exception {
        OrderCreatedEvent e = read("order-created-events", OrderCreatedEvent.class);
        requireEnvelope(e.eventId(), e.orderId(), e.timestamp());
        assertNotNull(e.customerId(), "customerId");
        assertNotNull(e.totalAmount(), "totalAmount");
    }

    @Test
    void consumesPetReserved() throws Exception {
        PetReservedEvent e = read("pet-reserved-events", PetReservedEvent.class);
        requireEnvelope(e.eventId(), e.orderId(), e.timestamp());
    }

    @Test
    void consumesPaymentSucceeded() throws Exception {
        PaymentSucceededEvent e = read("payment-succeeded-events", PaymentSucceededEvent.class);
        requireEnvelope(e.eventId(), e.orderId(), e.timestamp());
        assertNotNull(e.amount(), "amount");
    }

    @Test
    void consumesOrderOutcomes() throws Exception {
        OrderConfirmedEvent confirmed = read("order-confirmed-events", OrderConfirmedEvent.class);
        requireEnvelope(confirmed.eventId(), confirmed.orderId(), confirmed.timestamp());
        assertNotNull(confirmed.customerId(), "customerId");
        OrderCancelledEvent cancelled = read("order-cancelled-events", OrderCancelledEvent.class);
        requireEnvelope(cancelled.eventId(), cancelled.orderId(), cancelled.timestamp());
        assertNotNull(cancelled.reason(), "reason");
        OrderFailedEvent failed = read("order-failed-events", OrderFailedEvent.class);
        requireEnvelope(failed.eventId(), failed.orderId(), failed.timestamp());
        assertNotNull(failed.reason(), "reason");
    }

    @Test
    void consumesShipmentAndDelivery() throws Exception {
        ShipmentCreatedEvent shipment = read("shipment-created-events", ShipmentCreatedEvent.class);
        requireEnvelope(shipment.eventId(), shipment.orderId(), shipment.timestamp());
        assertNotNull(shipment.trackingNumber(), "trackingNumber");
        DeliveryStatusUpdatedEvent delivery = read("delivery-status-updated-events", DeliveryStatusUpdatedEvent.class);
        requireEnvelope(delivery.eventId(), delivery.orderId(), delivery.timestamp());
        assertNotNull(delivery.status(), "status");
    }

    @Test
    void consumesPaymentFailedAndRefunded() throws Exception {
        PaymentFailedEvent failed = read("payment-failed-events", PaymentFailedEvent.class);
        requireEnvelope(failed.eventId(), failed.orderId(), failed.timestamp());
        assertNotNull(failed.reason(), "reason");
        assertNotNull(failed.customerId(), "customerId");
        PaymentRefundedEvent refunded = read("payment-refunded-events", PaymentRefundedEvent.class);
        requireEnvelope(refunded.eventId(), refunded.orderId(), refunded.timestamp());
        assertNotNull(refunded.amount(), "amount");
        assertNotNull(refunded.customerId(), "customerId");
    }

    @Test
    void consumesNotificationSent() throws Exception {
        NotificationSentEvent e = read("notification-sent-events", NotificationSentEvent.class);
        requireEnvelope(e.eventId(), e.orderId(), e.timestamp());
        assertNotNull(e.customerId(), "customerId");
        assertNotNull(e.notificationType(), "notificationType");
        assertNotNull(e.message(), "message");
    }

    @Test
    void deliveryEventCarriesTheNote() throws Exception {
        assertNotNull(read("delivery-status-updated-events", DeliveryStatusUpdatedEvent.class).note(), "note");
    }

    @Test
    void consumesPetReservationFailed() throws Exception {
        PetReservationFailedEvent e = read("pet-reservation-failed-events", PetReservationFailedEvent.class);
        requireEnvelope(e.eventId(), e.orderId(), e.timestamp());
        assertNotNull(e.reason(), "reason");
    }

    private static void requireEnvelope(Object eventId, Object orderId, Object timestamp) {
        assertNotNull(eventId, "eventId");
        assertNotNull(orderId, "orderId");
        assertNotNull(timestamp, "timestamp");
    }

    static <T> T read(String topic, Class<T> type) throws Exception {
        String json = new ClassPathResource("contracts/" + topic + ".json").getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, type);
    }
}
