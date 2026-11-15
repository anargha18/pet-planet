package com.petcommerce.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.notification.domain.model.Notification;
import com.petcommerce.notification.domain.model.NotificationStatus;
import com.petcommerce.notification.domain.model.NotificationType;
import com.petcommerce.notification.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.notification.messaging.event.OrderCancelledEvent;
import com.petcommerce.notification.messaging.event.OrderConfirmedEvent;
import com.petcommerce.notification.messaging.event.OrderFailedEvent;
import com.petcommerce.notification.messaging.event.PaymentRefundedEvent;
import com.petcommerce.notification.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.notification.messaging.publisher.NotificationEventPublisher;
import com.petcommerce.notification.persistence.entity.NotificationOutboxEvent;
import com.petcommerce.notification.persistence.repository.NotificationOutboxRepository;
import com.petcommerce.notification.persistence.repository.NotificationRepository;
import com.petcommerce.notification.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Typed notifications (R-SAGA-09). */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID CORRELATION = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private NotificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(notificationOutboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new NotificationService(notificationRepository, processedEventRepository,
                new NotificationEventPublisher(notificationOutboxRepository, objectMapper));
    }

    @Test
    void confirmationSaysConfirmedNotOnTheWay() {
        Notification n = service.handleOrderConfirmed(new OrderConfirmedEvent(UUID.randomUUID(), "OrderConfirmedEvent", 1,
                UUID.randomUUID(), Instant.now(), CORRELATION, 10L, 7L, List.of(UUID.randomUUID()), BigDecimal.TEN));

        assertEquals(NotificationType.ORDER_CONFIRMED, n.getType());
        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertNotNull(n.getSentAt());
        assertTrue(n.getMessage().contains("confirmed"), n.getMessage());
        assertTrue(!n.getMessage().contains("on the way"), n.getMessage());
        NotificationOutboxEvent outbox = published();
        assertEquals("notification-sent-events", outbox.getTopic());
        assertTrue(outbox.getPayload().contains("\"notificationType\":\"ORDER_CONFIRMED\""), outbox.getPayload());
        assertTrue(outbox.getPayload().contains("\"customerId\":7"), outbox.getPayload());
    }

    @Test
    void cancellationWordingDependsOnReason() {
        Notification byCustomer = service.handleOrderCancelled(cancelled(11L, "CUSTOMER_CANCELLED"));
        Notification byExpiry = service.handleOrderCancelled(cancelled(12L, "RESERVATION_EXPIRED"));

        assertEquals(NotificationType.ORDER_CANCELLED, byCustomer.getType());
        assertTrue(byCustomer.getMessage().contains("as you requested"), byCustomer.getMessage());
        assertTrue(byExpiry.getMessage().contains("in time"), byExpiry.getMessage());
    }

    @Test
    void failureExplainsTheCause() {
        Notification n = service.handleOrderFailed(new OrderFailedEvent(UUID.randomUUID(), "OrderFailedEvent", 1,
                UUID.randomUUID(), Instant.now(), CORRELATION, 13L, 7L, "PAYMENT_FAILED:Card declined"));

        assertEquals(NotificationType.ORDER_FAILED, n.getType());
        assertTrue(n.getMessage().contains("payment was declined"), n.getMessage());
    }

    @Test
    void refundWithoutCustomerIdTakesOwnerFromEarlierNotification() {
        Notification earlier = new Notification(14L, 7L, NotificationType.ORDER_CANCELLED, "x", NotificationStatus.SENT);
        when(notificationRepository.findFirstByOrderId(14L)).thenReturn(Optional.of(earlier));

        Notification n = service.handlePaymentRefunded(refunded(14L, null));

        assertEquals(NotificationType.PAYMENT_REFUNDED, n.getType());
        assertEquals(7L, n.getCustomerId());
        assertTrue(n.getMessage().contains("123.40"), n.getMessage());
    }

    @Test
    void refundWithNoKnownOwnerFailsForTheDeadLetterTopic() {
        when(notificationRepository.findFirstByOrderId(15L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.handlePaymentRefunded(refunded(15L, null)));
    }

    @Test
    void shipmentAndDeliveryStatusesMapToTypes() {
        assertEquals(NotificationType.SHIPPED, service.handleShipmentCreated(new ShipmentCreatedEvent(UUID.randomUUID(),
                "ShipmentCreatedEvent", 1, UUID.randomUUID(), Instant.now(), CORRELATION, 16L, 7L, UUID.randomUUID(),
                "PET-16-X", "CREATED")).getType());
        assertNull(service.handleDeliveryStatusUpdated(delivery(16L, "IN_TRANSIT", null)));
        assertEquals(NotificationType.OUT_FOR_DELIVERY, service.handleDeliveryStatusUpdated(delivery(16L, "OUT_FOR_DELIVERY", null)).getType());
        assertEquals(NotificationType.DELIVERED, service.handleDeliveryStatusUpdated(delivery(16L, "DELIVERED", null)).getType());
        Notification failed = service.handleDeliveryStatusUpdated(delivery(17L, "DELIVERY_FAILED", "Nobody home"));
        assertEquals(NotificationType.DELIVERY_FAILED, failed.getType());
        assertTrue(failed.getMessage().contains("Nobody home"), failed.getMessage());
    }

    @Test
    void duplicateEventOrExistingTypeSendsNothing() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.markProcessed(eventId)).thenReturn(0);
        assertNull(service.handleOrderCancelled(new OrderCancelledEvent(eventId, "OrderCancelledEvent", 1,
                UUID.randomUUID(), Instant.now(), CORRELATION, 18L, 7L, "CUSTOMER_CANCELLED")));

        when(notificationRepository.existsByOrderIdAndType(19L, NotificationType.ORDER_CANCELLED)).thenReturn(true);
        assertNull(service.handleOrderCancelled(cancelled(19L, "CUSTOMER_CANCELLED")));

        verify(notificationRepository, never()).save(any());
        verify(notificationOutboxRepository, never()).save(any());
    }

    private NotificationOutboxEvent published() {
        ArgumentCaptor<NotificationOutboxEvent> captor = ArgumentCaptor.forClass(NotificationOutboxEvent.class);
        verify(notificationOutboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private static OrderCancelledEvent cancelled(Long orderId, String reason) {
        return new OrderCancelledEvent(UUID.randomUUID(), "OrderCancelledEvent", 1, UUID.randomUUID(), Instant.now(),
                CORRELATION, orderId, 7L, reason);
    }

    private static PaymentRefundedEvent refunded(Long orderId, Long customerId) {
        return new PaymentRefundedEvent(UUID.randomUUID(), "PaymentRefundedEvent", 1, UUID.randomUUID(), Instant.now(),
                CORRELATION, orderId, UUID.randomUUID(), new BigDecimal("123.4"), "CUSTOMER_CANCELLED", customerId);
    }

    private static DeliveryStatusUpdatedEvent delivery(Long orderId, String status, String note) {
        return new DeliveryStatusUpdatedEvent(UUID.randomUUID(), "DeliveryStatusUpdatedEvent", 1, UUID.randomUUID(),
                Instant.now(), CORRELATION, orderId, 7L, UUID.randomUUID(), "PET-" + orderId + "-X", status, note);
    }
}
