package com.petcommerce.shipping.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.shipping.application.ShippingService;
import com.petcommerce.shipping.domain.model.Shipment;
import com.petcommerce.shipping.domain.model.ShipmentStatus;
import com.petcommerce.shipping.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.shipping.messaging.event.OrderConfirmedEvent;
import com.petcommerce.shipping.messaging.publisher.ShipmentEventPublisher;
import com.petcommerce.shipping.persistence.repository.ProcessedEventRepository;
import com.petcommerce.shipping.persistence.repository.ShipmentOutboxRepository;
import com.petcommerce.shipping.persistence.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ShipmentOutboxRepository shipmentOutboxRepository;


    @Mock
    private ObjectMapper objectMapper;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() throws Exception {
        // Default: every event is seen for the first time; duplicate-handling tests override this.
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shipmentOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        shippingService = new ShippingService(
                shipmentRepository,
                processedEventRepository,
                new ShipmentEventPublisher(shipmentOutboxRepository, objectMapper)
        );
    }

    @Test
    void handleOrderConfirmedEventCreatesShipment() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 321L;
        when(processedEventRepository.markProcessed(eventId)).thenReturn(1);
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                101L,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(250.00)
        );

        Shipment shipment = shippingService.handleOrderConfirmedEvent(event);

        assertNotNull(shipment);
        assertEquals(orderId, shipment.getOrderId());
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
        assertNull(shipment.getShippedAt());
        assertNotNull(shipment.getTrackingNumber());
        verify(shipmentRepository, atLeastOnce()).save(any(Shipment.class));
    }

    @Test
    void duplicateOrderConfirmedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 421L;
        when(processedEventRepository.markProcessed(eventId)).thenReturn(0);

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                101L,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(180.00)
        );

        Shipment shipment = shippingService.handleOrderConfirmedEvent(event);

        assertNull(shipment);
    }

    @Test
    void deliveryProgressIsMirroredOntoTheShipment() {
        Shipment shipment = new Shipment(50L, 7L, "PET-50-X");
        when(shipmentRepository.findByOrderId(50L)).thenReturn(Optional.of(shipment));

        shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(50L, "IN_TRANSIT"));
        assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus());
        assertNotNull(shipment.getShippedAt());

        shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(50L, "OUT_FOR_DELIVERY"));
        assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus());

        shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(50L, "DELIVERED"));
        assertEquals(ShipmentStatus.DELIVERED, shipment.getStatus());
        assertNotNull(shipment.getDeliveredAt());
    }

    @Test
    void terminalShipmentNeverChanges() {
        Shipment shipment = new Shipment(51L, 7L, "PET-51-X");
        when(shipmentRepository.findByOrderId(51L)).thenReturn(Optional.of(shipment));

        shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(51L, "DELIVERY_FAILED"));
        shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(51L, "DELIVERED"));

        assertEquals(ShipmentStatus.DELIVERY_FAILED, shipment.getStatus());
        assertNull(shipment.getDeliveredAt());
    }

    @Test
    void deliveryEventForUnknownShipmentIsSkipped() {
        when(shipmentRepository.findByOrderId(52L)).thenReturn(Optional.empty());

        assertNull(shippingService.handleDeliveryStatusUpdatedEvent(deliveryEvent(52L, "DELIVERED")));
        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    private static DeliveryStatusUpdatedEvent deliveryEvent(Long orderId, String status) {
        return new DeliveryStatusUpdatedEvent(UUID.randomUUID(), "DeliveryStatusUpdatedEvent", 1, UUID.randomUUID(),
                Instant.now(), UUID.randomUUID(), orderId, 7L, UUID.randomUUID(), "PET-" + orderId + "-X", status);
    }
}
