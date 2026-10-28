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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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

    private Authentication authenticatedUser(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, "pw", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Long customerIdFor(String email) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Math.abs(java.nio.ByteBuffer.wrap(hash, 0, Long.BYTES).getLong());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        OrderResponse response = orderService.create(request, auth);

        assertNotNull(response);
        assertNotNull(response.customerId());
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

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(petId, 1),
                new CreateOrderItemRequest(petId, 1)
        ));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        OrderResponse response = orderService.create(request, auth);

        assertEquals(BigDecimal.valueOf(20000), response.totalAmount());
    }

    @Test
    void rejectQuantityGreaterThanOne() {
        UUID petId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 2)));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        assertThrows(IllegalArgumentException.class, () -> orderService.create(request, auth));
    }

    @Test
    void rejectMissingPet() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetNotFoundException(petId));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        assertThrows(PetNotFoundException.class, () -> orderService.create(request, auth));
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

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        assertThrows(PetUnavailableException.class, () -> orderService.create(request, auth));
    }

    @Test
    void getOrder() {
        Order order = new Order();
        order.setId(10L);
        Long customerId = customerIdFor("customer@example.com");
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.valueOf(75000));
        when(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order));

        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");
        assertDoesNotThrow(() -> orderService.getOrder(10L, auth));
    }

    @Test
    void orderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");
        assertThrows(RuntimeException.class, () -> orderService.getOrder(99L, auth));
    }

    @Test
    void cancelCreatedOrder() {
        Order order = new Order();
        order.setId(12L);
        Long customerId = customerIdFor("customer@example.com");
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");
        assertDoesNotThrow(() -> orderService.cancel(12L, auth));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void rejectCancellationOfInvalidState() {
        Order order = new Order();
        order.setId(13L);
        Long customerId = customerIdFor("customer@example.com");
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(13L)).thenReturn(java.util.Optional.of(order));

        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");
        assertThrows(InvalidOrderStateException.class, () -> orderService.cancel(13L, auth));
    }

    @Test
    void customerCannotAccessAnotherCustomersOrder() {
        Order otherCustomerOrder = new Order();
        otherCustomerOrder.setId(14L);
        otherCustomerOrder.setCustomerId(customerIdFor("other@example.com"));
        otherCustomerOrder.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(14L)).thenReturn(java.util.Optional.of(otherCustomerOrder));

        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");
        assertThrows(AccessDeniedException.class, () -> orderService.getOrder(14L, auth));
    }

    @Test
    void petCatalogDownReturnsServiceUnavailable() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetCatalogUnavailableException("Pet Catalog unavailable"));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("customer@example.com", "CUSTOMER");

        assertThrows(PetCatalogUnavailableException.class, () -> orderService.create(request, auth));
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
