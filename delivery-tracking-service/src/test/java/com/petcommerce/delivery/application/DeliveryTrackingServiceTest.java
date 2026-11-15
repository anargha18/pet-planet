package com.petcommerce.delivery.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.domain.exception.DeliveryNotFoundException;
import com.petcommerce.delivery.domain.exception.InvalidDeliveryTransitionException;
import com.petcommerce.delivery.domain.model.DeliveryEvent;
import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.messaging.publisher.DeliveryEventPublisher;
import com.petcommerce.delivery.persistence.entity.DeliveryOutboxEvent;
import com.petcommerce.delivery.persistence.repository.DeliveryEventRepository;
import com.petcommerce.delivery.persistence.repository.DeliveryOutboxRepository;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

    private static final UUID CORRELATION = UUID.randomUUID();

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @Mock
    private DeliveryEventRepository deliveryEventRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private DeliveryOutboxRepository deliveryOutboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private DeliveryTrackingService deliveryTrackingService;

    @BeforeEach
    void setUp() {
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(deliveryRecordRepository.save(any(DeliveryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(deliveryEventRepository.save(any(DeliveryEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(deliveryOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        deliveryTrackingService = new DeliveryTrackingService(
                deliveryRecordRepository,
                deliveryEventRepository,
                processedEventRepository,
                new DeliveryEventPublisher(deliveryOutboxRepository, objectMapper)
        );
    }

    @Test
    void shipmentCreatedStartsDeliveryInTransitWithTimelineAndEvent() {
        when(deliveryRecordRepository.findByOrderId(555L)).thenReturn(Optional.empty());

        DeliveryRecord delivery = deliveryTrackingService.handleShipmentCreatedEvent(shipmentCreated(UUID.randomUUID(), 555L));

        assertNotNull(delivery);
        assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
        assertEquals("PET-555-ABC123", delivery.getTrackingNumber());
        assertEquals(CORRELATION, delivery.getCorrelationId());
        verify(deliveryEventRepository).save(any(DeliveryEvent.class));
        DeliveryOutboxEvent outbox = publishedEvent();
        assertEquals("delivery-status-updated-events", outbox.getTopic());
        assertEquals(CORRELATION, outbox.getCorrelationId());
        assertTrue(outbox.getPayload().contains("\"status\":\"IN_TRANSIT\""), outbox.getPayload());
    }

    @Test
    void duplicateShipmentCreatedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.markProcessed(eventId)).thenReturn(0);

        assertNull(deliveryTrackingService.handleShipmentCreatedEvent(shipmentCreated(eventId, 777L)));
        verify(deliveryOutboxRepository, never()).save(any());
    }

    @Test
    void adminAdvancesDeliveryAndReusesSagaCorrelationId() {
        DeliveryRecord delivery = new DeliveryRecord(10L, 7L, "PET-10-X", CORRELATION);
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(delivery));

        DeliveryResponse response = deliveryTrackingService.advance(10L, DeliveryStatus.DELIVERED, "Left at the door");

        assertEquals(DeliveryStatus.DELIVERED, response.status());
        assertNotNull(response.deliveredAt());
        DeliveryOutboxEvent outbox = publishedEvent();
        assertEquals(CORRELATION, outbox.getCorrelationId());
        assertTrue(outbox.getPayload().contains("\"status\":\"DELIVERED\""), outbox.getPayload());
        assertTrue(outbox.getPayload().contains("\"previousStatus\":\"IN_TRANSIT\""), outbox.getPayload());
        assertTrue(outbox.getPayload().contains("\"note\":\"Left at the door\""), outbox.getPayload());
    }

    @Test
    void illegalTransitionIsRejectedWithoutSideEffects() {
        DeliveryRecord delivery = new DeliveryRecord(10L, 7L, "PET-10-X", CORRELATION);
        delivery.advanceTo(DeliveryStatus.DELIVERED, Instant.now());
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(delivery));

        assertThrows(InvalidDeliveryTransitionException.class,
                () -> deliveryTrackingService.advance(10L, DeliveryStatus.OUT_FOR_DELIVERY, null));
        verify(deliveryOutboxRepository, never()).save(any());
        verify(deliveryEventRepository, never()).save(any());
    }

    @Test
    void advancingUnknownOrderIsNotFound() {
        when(deliveryRecordRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThrows(DeliveryNotFoundException.class,
                () -> deliveryTrackingService.advance(99L, DeliveryStatus.DELIVERED, null));
    }

    private DeliveryOutboxEvent publishedEvent() {
        ArgumentCaptor<DeliveryOutboxEvent> captor = ArgumentCaptor.forClass(DeliveryOutboxEvent.class);
        verify(deliveryOutboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ShipmentCreatedEvent shipmentCreated(UUID eventId, Long orderId) {
        return new ShipmentCreatedEvent(eventId, "ShipmentCreatedEvent", 1, UUID.randomUUID(), Instant.now(), CORRELATION,
                orderId, 101L, UUID.randomUUID(), "PET-" + orderId + "-ABC123", "CREATED");
    }
}
