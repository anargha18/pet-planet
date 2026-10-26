package com.petcommerce.order.service;

import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.dto.CreateOrderItemRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.dto.CreateOrderRequest;
import com.petcommerce.order.dto.OrderResponse;
import com.petcommerce.order.entity.Order;
import com.petcommerce.order.entity.OrderStatus;
import com.petcommerce.order.event.PaymentFailedEvent;
import com.petcommerce.order.event.PaymentSucceededEvent;
import com.petcommerce.order.exception.InvalidOrderStateException;
import com.petcommerce.order.exception.PetCatalogUnavailableException;
import com.petcommerce.order.exception.PetNotFoundException;
import com.petcommerce.order.exception.PetUnavailableException;
import com.petcommerce.order.repository.OrderItemRepository;
import com.petcommerce.order.repository.OrderOutboxRepository;
import com.petcommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PetCatalogClient petCatalogClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(orderOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createOrderSuccessfully() {
        UUID petId = UUID.randomUUID();
        PetCatalogPetResponse pet = new PetCatalogPetResponse(
                petId,
                "Max",
                com.petcommerce.order.entity.Species.DOG,
                "Golden Retriever",
                BigDecimal.valueOf(75000),
                "AVAILABLE"
        );

        when(petCatalogClient.getPetById(petId)).thenReturn(pet);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1001L);
            return order;
        });

        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(new CreateOrderItemRequest(petId, 1)));

        OrderResponse response = orderService.create(request);

        assertNotNull(response);
        assertEquals(101L, response.customerId());
        assertEquals(OrderStatus.CREATED, response.status());
        assertEquals(BigDecimal.valueOf(75000), response.totalAmount());
        assertEquals(1, response.items().size());
        assertEquals("Max", response.items().get(0).petName());
    }

    @Test
    void calculateTotalCorrectly() {
        UUID petId = UUID.randomUUID();
        PetCatalogPetResponse pet = new PetCatalogPetResponse(
                petId,
                "Max",
                com.petcommerce.order.entity.Species.DOG,
                "Golden Retriever",
                BigDecimal.valueOf(10000),
                "AVAILABLE"
        );

        when(petCatalogClient.getPetById(petId)).thenReturn(pet);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1002L);
            return order;
        });

        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(
                new CreateOrderItemRequest(petId, 1),
                new CreateOrderItemRequest(petId, 1)
        ));

        OrderResponse response = orderService.create(request);

        assertEquals(BigDecimal.valueOf(20000), response.totalAmount());
    }

    @Test
    void rejectQuantityGreaterThanOne() {
        UUID petId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(new CreateOrderItemRequest(petId, 2)));

        assertThrows(IllegalArgumentException.class, () -> orderService.create(request));
    }

    @Test
    void rejectMissingPet() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetNotFoundException(petId));

        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(new CreateOrderItemRequest(petId, 1)));

        assertThrows(PetNotFoundException.class, () -> orderService.create(request));
    }

    @Test
    void rejectUnavailablePet() {
        UUID petId = UUID.randomUUID();
        PetCatalogPetResponse pet = new PetCatalogPetResponse(
                petId,
                "Max",
                com.petcommerce.order.entity.Species.DOG,
                "Golden Retriever",
                BigDecimal.valueOf(75000),
                "SOLD"
        );
        when(petCatalogClient.getPetById(petId)).thenReturn(pet);

        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(new CreateOrderItemRequest(petId, 1)));

        assertThrows(PetUnavailableException.class, () -> orderService.create(request));
    }

    @Test
    void getOrder() {
        Order order = new Order();
        order.setId(10L);
        order.setCustomerId(101L);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.valueOf(75000));
        when(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order));

        assertDoesNotThrow(() -> orderService.getOrder(10L));
    }

    @Test
    void orderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThrows(RuntimeException.class, () -> orderService.getOrder(99L));
    }

    @Test
    void cancelCreatedOrder() {
        Order order = new Order();
        order.setId(12L);
        order.setCustomerId(101L);
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        assertDoesNotThrow(() -> orderService.cancel(12L));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void rejectCancellationOfInvalidState() {
        Order order = new Order();
        order.setId(13L);
        order.setCustomerId(101L);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(13L)).thenReturn(java.util.Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancel(13L));
    }

    @Test
    void petCatalogDownReturnsServiceUnavailable() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetCatalogUnavailableException("Pet Catalog unavailable"));

        CreateOrderRequest request = new CreateOrderRequest(101L, List.of(new CreateOrderItemRequest(petId, 1)));

        assertThrows(PetCatalogUnavailableException.class, () -> orderService.create(request));
    }

    @Test
    void handlePaymentSucceededMarksOrderPaid() {
        Order order = new Order();
        order.setId(20L);
        order.setCustomerId(101L);
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(20L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID(),
                "PaymentSucceededEvent",
                1,
                UUID.randomUUID(),
                java.time.Instant.now(),
                UUID.randomUUID(),
                20L,
                UUID.randomUUID(),
                BigDecimal.valueOf(75000)
        );

        assertDoesNotThrow(() -> orderService.handlePaymentSucceeded(event));
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void handlePaymentFailedMarksOrderFailed() {
        Order order = new Order();
        order.setId(21L);
        order.setCustomerId(101L);
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(21L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                "PaymentFailedEvent",
                1,
                UUID.randomUUID(),
                java.time.Instant.now(),
                UUID.randomUUID(),
                21L,
                UUID.randomUUID(),
                BigDecimal.valueOf(75000),
                "PAYMENT_DECLINED"
        );

        assertDoesNotThrow(() -> orderService.handlePaymentFailed(event));
        assertEquals(OrderStatus.FAILED, order.getStatus());
    }
}
