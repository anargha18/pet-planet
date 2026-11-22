package com.petcommerce.fulfillment.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Timeline texts are shown to customers as they are, so they must read naturally (no raw enum codes). */
class OrderTrackingTextTest {

    @Test
    void reasonCodesReadAsWords() {
        assertEquals("customer cancelled", OrderTrackingProjector.reason("CUSTOMER_CANCELLED"));
        assertEquals("pet already sold", OrderTrackingProjector.reason("PET_ALREADY_SOLD"));
        assertEquals("unknown", OrderTrackingProjector.reason(null));
        assertEquals("reservation failed: pet already reserved", OrderTrackingProjector.reason("RESERVATION_FAILED:PET_ALREADY_RESERVED"));
    }

    @Test
    void deliveryStatusesReadAsWords() {
        assertEquals("In transit", OrderTrackingProjector.deliveryText("IN_TRANSIT"));
        assertEquals("Out for delivery", OrderTrackingProjector.deliveryText("OUT_FOR_DELIVERY"));
        assertEquals("Delivered", OrderTrackingProjector.deliveryText("DELIVERED"));
        assertEquals("Delivery failed", OrderTrackingProjector.deliveryText("DELIVERY_FAILED"));
        assertEquals("Delivery status: returned to sender", OrderTrackingProjector.deliveryText("RETURNED_TO_SENDER"));
    }
}
