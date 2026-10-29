package com.petcommerce.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.delivery.entity.DeliveryRecord;
import com.petcommerce.delivery.entity.DeliveryStatus;
import com.petcommerce.delivery.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.repository.DeliveryOutboxRepository;
import com.petcommerce.delivery.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.repository.ProcessedEventRepository;
import com.petcommerce.delivery.service.DeliveryTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
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
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private DeliveryTrackingService deliveryTrackingService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(deliveryRecordRepository.save(any(DeliveryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(deliveryOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        deliveryTrackingService = new DeliveryTrackingService(
                deliveryRecordRepository,
                processedEventRepository,
                deliveryOutboxRepository,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void handleShipmentCreatedEventCreatesDeliveryTracking() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 555L;
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(deliveryRecordRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                eventId,
                "ShipmentCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
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
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                eventId,
                "ShipmentCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "PET-777-XYZ999",
                "SHIPPED"
        );

        DeliveryRecord delivery = deliveryTrackingService.handleShipmentCreatedEvent(event);

        assertNull(delivery);
    }
}
