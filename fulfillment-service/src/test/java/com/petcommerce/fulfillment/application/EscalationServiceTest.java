package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.model.EscalationReason;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalationServiceTest {

    @Test
    void ordersStuckBeforeConfirmationAreEscalatedWithATimelineEntry() {
        OrderTrackingRepository trackingRepository = org.mockito.Mockito.mock(OrderTrackingRepository.class);
        OrderTrackingEventRepository timelineRepository = org.mockito.Mockito.mock(OrderTrackingEventRepository.class);
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        OrderTracking stuck = new OrderTracking(10L);
        stuck.applyOrderStatus(TrackedOrderStatus.PAYMENT_PENDING, now.minusSeconds(3600));
        when(trackingRepository.findByEscalatedFalseAndOrderStatusInAndOrderStatusAtBefore(
                EnumSet.of(TrackedOrderStatus.CREATED, TrackedOrderStatus.PAYMENT_PENDING), now.minus(Duration.ofMinutes(10))))
                .thenReturn(List.of(stuck));

        EscalationService service = new EscalationService(trackingRepository, timelineRepository,
                Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(1, service.escalateStuckOrders());
        assertTrue(stuck.isEscalated());
        assertEquals(EscalationReason.STUCK_BEFORE_CONFIRMATION, stuck.getEscalationReason());
        verify(timelineRepository).insertIfAbsent(any(), eq(10L), eq("Escalated"), eq("fulfillment-service"), any(), eq(now));
    }
}
