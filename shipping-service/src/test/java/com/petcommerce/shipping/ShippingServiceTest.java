package com.petcommerce.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.shipping.entity.Shipment;
import com.petcommerce.shipping.entity.ShipmentStatus;
import com.petcommerce.shipping.event.OrderConfirmedEvent;
import com.petcommerce.shipping.repository.ShipmentOutboxRepository;
import com.petcommerce.shipping.repository.ShipmentRepository;
import com.petcommerce.shipping.repository.ProcessedEventRepository;
import com.petcommerce.shipping.service.ShippingService;
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
class ShippingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ShipmentOutboxRepository shipmentOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shipmentOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        shippingService = new ShippingService(
                shipmentRepository,
                processedEventRepository,
                shipmentOutboxRepository,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void handleOrderConfirmedEventCreatesShipment() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 321L;
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(250.00)
        );

        Shipment shipment = shippingService.handleOrderConfirmedEvent(event);

        assertNotNull(shipment);
        assertEquals(orderId, shipment.getOrderId());
        assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus());
        assertNotNull(shipment.getTrackingNumber());
        verify(shipmentRepository, atLeastOnce()).save(any(Shipment.class));
    }

    @Test
    void duplicateOrderConfirmedEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        Long orderId = 421L;
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                List.of(UUID.randomUUID()),
                BigDecimal.valueOf(180.00)
        );

        Shipment shipment = shippingService.handleOrderConfirmedEvent(event);

        assertNull(shipment);
    }
}
