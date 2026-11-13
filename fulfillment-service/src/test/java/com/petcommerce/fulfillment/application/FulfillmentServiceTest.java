package com.petcommerce.fulfillment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.fulfillment.application.FulfillmentService;
import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import com.petcommerce.fulfillment.domain.model.FulfillmentStatus;
import com.petcommerce.fulfillment.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.messaging.publisher.FulfillmentEventPublisher;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentOutboxRepository;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentRecordRepository;
import com.petcommerce.fulfillment.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock
    private FulfillmentRecordRepository fulfillmentRecordRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private FulfillmentOutboxRepository fulfillmentOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private FulfillmentService fulfillmentService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(fulfillmentRecordRepository.save(any(FulfillmentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(fulfillmentOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        fulfillmentService = new FulfillmentService(
                fulfillmentRecordRepository,
                processedEventRepository,
                new FulfillmentEventPublisher(fulfillmentOutboxRepository, kafkaTemplate, objectMapper)
        );
    }

    @Test
    void handleDeliveryStatusUpdatedEventCreatesFulfillmentRecord() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 888L;
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(fulfillmentRecordRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                eventId,
                "DeliveryStatusUpdatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "PET-888-TRACKING",
                "IN_TRANSIT"
        );

        FulfillmentRecord record = fulfillmentService.handleDeliveryStatusUpdatedEvent(event);

        assertNotNull(record);
        assertEquals(orderId, record.getOrderId());
        assertEquals(FulfillmentStatus.SHIPPED, record.getStatus());
        assertEquals("PET-888-TRACKING", record.getTrackingNumber());
        verify(fulfillmentRecordRepository, atLeastOnce()).save(any(FulfillmentRecord.class));
    }

    @Test
    void handleDeliveryStatusUpdatedEventUpdatesExistingRecordToDelivered() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 889L;
        FulfillmentRecord existing = new FulfillmentRecord(orderId, 101L, "PET-889-TRACKING", FulfillmentStatus.SHIPPED, "Initial");
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(fulfillmentRecordRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                eventId,
                "DeliveryStatusUpdatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "PET-889-TRACKING",
                "DELIVERED"
        );

        FulfillmentRecord record = fulfillmentService.handleDeliveryStatusUpdatedEvent(event);

        assertNotNull(record);
        assertEquals(FulfillmentStatus.DELIVERED, record.getStatus());
        verify(fulfillmentRecordRepository, atLeastOnce()).save(existing);
    }

    @Test
    void duplicateDeliveryStatusUpdatedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 999L;
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                eventId,
                "DeliveryStatusUpdatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "PET-999-TRACKING",
                "DELIVERED"
        );

        FulfillmentRecord record = fulfillmentService.handleDeliveryStatusUpdatedEvent(event);

        assertNull(record);
    }
}
