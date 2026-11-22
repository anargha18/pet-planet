package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import com.petcommerce.fulfillment.domain.model.TrackedPaymentStatus;
import com.petcommerce.fulfillment.messaging.event.OrderCreatedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentRefundedEvent;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingProjectorTest {

    @Mock
    private OrderTrackingRepository trackingRepository;

    @Mock
    private OrderTrackingEventRepository timelineRepository;

    @InjectMocks
    private OrderTrackingProjector projector;

    private final OrderTracking tracking = new OrderTracking(10L);

    @BeforeEach
    void setUp() {
        when(trackingRepository.lockByOrderId(10L)).thenReturn(Optional.of(tracking));
    }

    @Test
    void firstSightOfAnEventUpdatesStateAndTimeline() {
        when(timelineRepository.insertIfAbsent(any(), eq(10L), eq("OrderCreated"), eq("order-service"), eq("Order placed (total $123.40)"), any()))
                .thenReturn(1);

        assertTrue(projector.onOrderCreated(new OrderCreatedEvent(UUID.randomUUID(), "OrderCreatedEvent", Instant.now(),
                UUID.randomUUID(), 10L, 7L, new BigDecimal("123.4"))));

        verify(trackingRepository).insertIfAbsent(10L);
        assertEquals(7L, tracking.getCustomerId());
        assertEquals(TrackedOrderStatus.CREATED, tracking.getOrderStatus());
        verify(trackingRepository).save(tracking);
    }

    @Test
    void replayedEventChangesNothing() {
        when(timelineRepository.insertIfAbsent(any(), eq(10L), anyString(), anyString(), anyString(), any())).thenReturn(0);

        assertFalse(projector.onPaymentRefunded(new PaymentRefundedEvent(UUID.randomUUID(), "PaymentRefundedEvent",
                Instant.now(), UUID.randomUUID(), 10L, BigDecimal.TEN, "ORDER_CANCELLED", 7L)));

        assertEquals(null, tracking.getPaymentStatus());
        verify(trackingRepository, never()).save(any());
    }

    @Test
    void refundIsProjected() {
        when(timelineRepository.insertIfAbsent(any(), eq(10L), anyString(), anyString(), anyString(), any())).thenReturn(1);

        projector.onPaymentRefunded(new PaymentRefundedEvent(UUID.randomUUID(), "PaymentRefundedEvent",
                Instant.now(), UUID.randomUUID(), 10L, BigDecimal.TEN, "ORDER_CANCELLED", null));

        assertEquals(TrackedPaymentStatus.REFUNDED, tracking.getPaymentStatus());
    }
}
