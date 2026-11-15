package com.petcommerce.delivery.domain.model;

import com.petcommerce.delivery.domain.exception.InvalidDeliveryTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Delivery transition rules (R-SAGA-06). */
class DeliveryStatusTest {

    @ParameterizedTest
    @CsvSource({
            "IN_TRANSIT,OUT_FOR_DELIVERY,true",
            "IN_TRANSIT,DELIVERED,true",
            "IN_TRANSIT,DELIVERY_FAILED,true",
            "IN_TRANSIT,IN_TRANSIT,false",
            "OUT_FOR_DELIVERY,DELIVERED,true",
            "OUT_FOR_DELIVERY,DELIVERY_FAILED,true",
            "OUT_FOR_DELIVERY,IN_TRANSIT,false",
            "DELIVERED,DELIVERY_FAILED,false",
            "DELIVERED,OUT_FOR_DELIVERY,false",
            "DELIVERY_FAILED,DELIVERED,false"
    })
    void transitions(DeliveryStatus from, DeliveryStatus to, boolean allowed) {
        assertEquals(allowed, from.canMoveTo(to));
    }

    @Test
    void deliveredSetsDeliveredAtOnlyThen() {
        DeliveryRecord record = new DeliveryRecord(1L, 7L, "T", null);
        Instant t = Instant.parse("2026-11-15T10:00:00Z");
        record.advanceTo(DeliveryStatus.OUT_FOR_DELIVERY, t);
        assertNull(record.getDeliveredAt());
        record.advanceTo(DeliveryStatus.DELIVERED, t.plusSeconds(60));
        assertEquals(t.plusSeconds(60), record.getDeliveredAt());
        assertThrows(InvalidDeliveryTransitionException.class, () -> record.advanceTo(DeliveryStatus.DELIVERY_FAILED, t));
    }
}
