package com.petcommerce.delivery.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.delivery.application.DeliveryTrackingService;
import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.messaging.publisher.DeliveryEventPublisher;
import com.petcommerce.delivery.persistence.repository.DeliveryOutboxRepository;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private DeliveryOutboxRepository deliveryOutboxRepository;


    @Mock
    private ObjectMapper objectMapper;

    private DeliveryTrackingService deliveryTrackingService;

    @BeforeEach
    void setUp() throws Exception {
        // Default: every event is seen for the first time; duplicate-handling tests override this.
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(deliveryRecordRepository.save(any(DeliveryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(deliveryOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        deliveryTrackingService = new DeliveryTrackingService(
                deliveryRecordRepository,
                processedEventRepository,
                new DeliveryEventPublisher(deliveryOutboxRepository, objectMapper)
        );
    }

    @Test
    void handleShipmentCreatedEventCreatesDeliveryTracking() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 555L;
        when(processedEventRepository.markProcessed(eventId)).thenReturn(1);
        when(deliveryRecordRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                eventId,
                "ShipmentCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                101L,
                UUID.randomUUID(),
                "PET-555-ABC123",
                "SHIPPED"
        );

        DeliveryRecord delivery = deliveryTrackingService.handleShipmentCreatedEvent(event);

        assertNotNull(delivery);
        assertEquals(orderId, delivery.getOrderId());
        assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
        assertEquals("PET-555-ABC123", delivery.getTrackingNumber());
        verify(deliveryRecordRepository, atLeastOnce()).save(any(DeliveryRecord.class));
    }

    @Test
    void duplicateShipmentCreatedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 777L;
        when(processedEventRepository.markProcessed(eventId)).thenReturn(0);

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                eventId,
                "ShipmentCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                101L,
                UUID.randomUUID(),
                "PET-777-XYZ999",
                "SHIPPED"
        );

        DeliveryRecord delivery = deliveryTrackingService.handleShipmentCreatedEvent(event);

        assertNull(delivery);
    }
}
