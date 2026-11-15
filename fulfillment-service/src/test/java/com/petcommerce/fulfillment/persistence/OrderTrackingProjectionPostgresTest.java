package com.petcommerce.fulfillment.persistence;

import com.petcommerce.fulfillment.application.OrderTrackingProjector;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import com.petcommerce.fulfillment.domain.model.TrackedPaymentStatus;
import com.petcommerce.fulfillment.domain.model.TrackedShipmentStatus;
import com.petcommerce.fulfillment.messaging.event.OrderConfirmedEvent;
import com.petcommerce.fulfillment.messaging.event.OrderCreatedEvent;
import com.petcommerce.fulfillment.messaging.event.PaymentSucceededEvent;
import com.petcommerce.fulfillment.messaging.event.PetReservedEvent;
import com.petcommerce.fulfillment.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tracking projection against a real PostgreSQL 16 with the real Flyway migrations (R-TEST-01, part). Unit tests
 * mock the repositories; this proves the native SQL the projection relies on: insert-if-absent of the order row,
 * the row lock, and timeline-based idempotency, including several listener threads hitting one new order at once.
 * Each projector call runs in its own transaction (the test itself is not transactional).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(OrderTrackingProjector.class)
@Testcontainers
class OrderTrackingProjectionPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicLong ORDER_IDS = new AtomicLong(1000);

    @Autowired
    private OrderTrackingProjector projector;

    @Autowired
    private OrderTrackingRepository trackingRepository;

    @Autowired
    private OrderTrackingEventRepository timelineRepository;

    @Test
    void eventsArrivingOutOfOrderStillGiveTheRightView() {
        long orderId = ORDER_IDS.incrementAndGet();
        Instant t = Instant.parse("2026-11-15T10:00:00Z");

        projector.onOrderConfirmed(new OrderConfirmedEvent(UUID.randomUUID(), "OrderConfirmedEvent", t.plusSeconds(3),
                UUID.randomUUID(), orderId, 7L, new BigDecimal("50.00")));
        projector.onPetReserved(new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", t.plusSeconds(1),
                UUID.randomUUID(), orderId, 7L, new BigDecimal("50.00")));
        projector.onOrderCreated(new OrderCreatedEvent(UUID.randomUUID(), "OrderCreatedEvent", t,
                UUID.randomUUID(), orderId, 7L, new BigDecimal("50.00")));

        OrderTracking tracking = trackingRepository.findById(orderId).orElseThrow();
        assertEquals(TrackedOrderStatus.CONFIRMED, tracking.getOrderStatus());
        assertEquals(7L, tracking.getCustomerId());
        assertEquals(0, new BigDecimal("50.00").compareTo(tracking.getTotalAmount()));
        assertEquals(List.of("OrderCreated", "PetReserved", "OrderConfirmed"),
                timelineRepository.findByOrderIdOrderByOccurredAtAscRecordedAtAsc(orderId).stream()
                        .map(e -> e.getEventType()).toList());
    }

    @Test
    void replayedEventIsRecordedOnceAndChangesNothing() {
        long orderId = ORDER_IDS.incrementAndGet();
        OrderCreatedEvent created = new OrderCreatedEvent(UUID.randomUUID(), "OrderCreatedEvent", Instant.now(),
                UUID.randomUUID(), orderId, 7L, BigDecimal.TEN);

        assertTrue(projector.onOrderCreated(created));
        assertFalse(projector.onOrderCreated(created));

        assertEquals(1, timelineRepository.findByOrderIdOrderByOccurredAtAscRecordedAtAsc(orderId).size());
    }

    @Test
    void concurrentListenersOnOneNewOrderNeitherFailNorLoseUpdates() throws Exception {
        long orderId = ORDER_IDS.incrementAndGet();
        Instant t = Instant.now();
        List<Callable<Boolean>> events = List.of(
                () -> projector.onOrderCreated(new OrderCreatedEvent(UUID.randomUUID(), "OrderCreatedEvent", t,
                        UUID.randomUUID(), orderId, 7L, BigDecimal.TEN)),
                () -> projector.onPetReserved(new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", t.plusMillis(1),
                        UUID.randomUUID(), orderId, 7L, BigDecimal.TEN)),
                () -> projector.onPaymentSucceeded(new PaymentSucceededEvent(UUID.randomUUID(), "PaymentSucceededEvent",
                        t.plusMillis(2), UUID.randomUUID(), orderId, BigDecimal.TEN, 7L)),
                () -> projector.onOrderConfirmed(new OrderConfirmedEvent(UUID.randomUUID(), "OrderConfirmedEvent",
                        t.plusMillis(3), UUID.randomUUID(), orderId, 7L, BigDecimal.TEN)),
                () -> projector.onShipmentCreated(new ShipmentCreatedEvent(UUID.randomUUID(), "ShipmentCreatedEvent",
                        t.plusMillis(4), UUID.randomUUID(), orderId, 7L, "PET-" + orderId)));

        ExecutorService pool = Executors.newFixedThreadPool(events.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Callable<Boolean> event : events) {
            results.add(pool.submit(() -> {
                start.await();
                return event.call();
            }));
        }
        start.countDown();
        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "every event is applied exactly once");
        }
        pool.shutdown();

        OrderTracking tracking = trackingRepository.findById(orderId).orElseThrow();
        assertEquals(TrackedOrderStatus.CONFIRMED, tracking.getOrderStatus());
        assertEquals(TrackedPaymentStatus.SUCCEEDED, tracking.getPaymentStatus());
        assertEquals(TrackedShipmentStatus.CREATED, tracking.getShipmentStatus());
        assertEquals("PET-" + orderId, tracking.getTrackingNumber());
        assertEquals(5, timelineRepository.findByOrderIdOrderByOccurredAtAscRecordedAtAsc(orderId).size());
    }

    @Test
    void escalationQueryFindsOnlyOrdersStuckBeforeConfirmation() {
        long stuck = ORDER_IDS.incrementAndGet();
        long confirmed = ORDER_IDS.incrementAndGet();
        Instant old = Instant.now().minusSeconds(3600);
        projector.onPetReserved(new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", old, UUID.randomUUID(),
                stuck, 7L, BigDecimal.ONE));
        projector.onOrderConfirmed(new OrderConfirmedEvent(UUID.randomUUID(), "OrderConfirmedEvent", old,
                UUID.randomUUID(), confirmed, 7L, BigDecimal.ONE));

        List<Long> found = trackingRepository.findByEscalatedFalseAndOrderStatusInAndOrderStatusAtBefore(
                        List.of(TrackedOrderStatus.CREATED, TrackedOrderStatus.PAYMENT_PENDING), Instant.now().minusSeconds(600))
                .stream().map(OrderTracking::getOrderId).toList();

        assertTrue(found.contains(stuck));
        assertFalse(found.contains(confirmed));
    }
}
