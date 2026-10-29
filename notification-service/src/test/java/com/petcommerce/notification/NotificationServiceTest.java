package com.petcommerce.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.notification.entity.Notification;
import com.petcommerce.notification.entity.NotificationStatus;
import com.petcommerce.notification.event.OrderConfirmedEvent;
import com.petcommerce.notification.repository.NotificationOutboxRepository;
import com.petcommerce.notification.repository.NotificationRepository;
import com.petcommerce.notification.repository.ProcessedEventRepository;
import com.petcommerce.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(notificationOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        notificationService = new NotificationService(
                notificationRepository,
                processedEventRepository,
                notificationOutboxRepository,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void handleOrderConfirmedEventSendsNotification() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 123L;
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(notificationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(120.00)
        );

        Notification notification = notificationService.handleOrderConfirmedEvent(event);

        assertNotNull(notification);
        assertEquals(orderId, notification.getOrderId());
        assertEquals(NotificationStatus.SENT, notification.getStatus());
        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    void getNotificationsByCustomerIdReturnsCustomerNotifications() {
        Long customerId = 202L;
        when(notificationRepository.findByCustomerId(customerId)).thenReturn(List.of(new Notification(100L, customerId, "Hello", NotificationStatus.SENT)));

        List<Notification> notifications = notificationService.getNotificationsByCustomerId(customerId);

        assertEquals(1, notifications.size());
        assertEquals(customerId, notifications.get(0).getCustomerId());
    }

    @Test
    void duplicateOrderConfirmedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 456L;
        when(processedEventRepository.existsById(eventId)).thenReturn(true);
        when(notificationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(75.00)
        );

        Notification notification = notificationService.handleOrderConfirmedEvent(event);

        assertNull(notification);
    }
}
