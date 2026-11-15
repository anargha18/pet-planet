package com.petcommerce.fulfillment.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The projection must be correct whatever order events from different topics arrive in. */
class OrderTrackingTest {

    private final Instant t = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void orderStatusNeverMovesBackwardsOrLeavesTerminal() {
        OrderTracking tracking = new OrderTracking(1L);
        tracking.applyOrderStatus(TrackedOrderStatus.CONFIRMED, t);
        tracking.applyOrderStatus(TrackedOrderStatus.PAYMENT_PENDING, t);
        tracking.applyOrderStatus(TrackedOrderStatus.CREATED, t);
        tracking.applyOrderStatus(TrackedOrderStatus.CANCELLED, t);

        assertEquals(TrackedOrderStatus.CONFIRMED, tracking.getOrderStatus());
    }

    @Test
    void refundWinsAndLateOutcomeDoesNotOverwriteIt() {
        OrderTracking tracking = new OrderTracking(1L);
        tracking.applyPaymentStatus(TrackedPaymentStatus.PENDING);
        tracking.applyPaymentStatus(TrackedPaymentStatus.REFUNDED);
        tracking.applyPaymentStatus(TrackedPaymentStatus.SUCCEEDED);
        tracking.applyPaymentStatus(TrackedPaymentStatus.PENDING);

        assertEquals(TrackedPaymentStatus.REFUNDED, tracking.getPaymentStatus());
    }

    @Test
    void deliveryMirrorsShipmentAndFailureEscalates() {
        OrderTracking tracking = new OrderTracking(1L);
        tracking.applyDelivery(TrackedDeliveryStatus.IN_TRANSIT, "PET-1");
        assertEquals(TrackedShipmentStatus.SHIPPED, tracking.getShipmentStatus());
        assertEquals("PET-1", tracking.getTrackingNumber());

        tracking.applyShipment("PET-1"); // ShipmentCreated arriving late does not reset SHIPPED
        assertEquals(TrackedShipmentStatus.SHIPPED, tracking.getShipmentStatus());

        tracking.applyDelivery(TrackedDeliveryStatus.DELIVERY_FAILED, "PET-1");
        tracking.applyDelivery(TrackedDeliveryStatus.DELIVERED, "PET-1");
        assertEquals(TrackedDeliveryStatus.DELIVERY_FAILED, tracking.getDeliveryStatus());
        assertEquals(TrackedShipmentStatus.DELIVERY_FAILED, tracking.getShipmentStatus());
        assertTrue(tracking.isEscalated());
        assertEquals(EscalationReason.DELIVERY_FAILED, tracking.getEscalationReason());
    }

    @Test
    void stuckEscalationIsClearedWhenTheOrderCloses() {
        OrderTracking tracking = new OrderTracking(1L);
        tracking.applyOrderStatus(TrackedOrderStatus.PAYMENT_PENDING, t);
        tracking.escalate(EscalationReason.STUCK_BEFORE_CONFIRMATION);

        tracking.applyOrderStatus(TrackedOrderStatus.CONFIRMED, t.plusSeconds(900));

        assertFalse(tracking.isEscalated());
        assertNull(tracking.getEscalationReason());
    }

    @Test
    void customerIsTakenFromTheFirstEventThatKnowsIt() {
        OrderTracking tracking = new OrderTracking(1L);
        tracking.knowCustomer(null);
        tracking.knowCustomer(7L);
        tracking.knowCustomer(8L);
        assertEquals(7L, tracking.getCustomerId());
    }
}
