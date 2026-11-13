package com.petcommerce.order.application;

import com.petcommerce.order.api.dto.CreateOrderItemRequest;
import com.petcommerce.order.api.dto.CreateOrderRequest;
import com.petcommerce.order.api.dto.OrderResponse;
import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.domain.exception.InvalidOrderStateException;
import com.petcommerce.order.domain.exception.PetCatalogUnavailableException;
import com.petcommerce.order.domain.exception.PetNotFoundException;
import com.petcommerce.order.domain.exception.PetUnavailableException;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import com.petcommerce.order.security.OrderAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private PetCatalogClient petCatalogClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Spy
    private OrderAccessPolicy orderAccessPolicy = new OrderAccessPolicy();

    @InjectMocks
    private OrderCommandService orderCommandService;

    private Authentication authenticatedUser(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, "pw", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void createOrderSuccessfully() {
        UUID petId = UUID.randomUUID();
        PetCatalogPetResponse pet = new PetCatalogPetResponse(
                petId,
                "Max",
                com.petcommerce.order.domain.model.Species.DOG,
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
        Authentication auth = authenticatedUser("7", "USER");

        OrderResponse response = orderCommandService.create(request, auth);

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
                com.petcommerce.order.domain.model.Species.DOG,
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
        Authentication auth = authenticatedUser("7", "USER");

        OrderResponse response = orderCommandService.create(request, auth);

        assertEquals(BigDecimal.valueOf(20000), response.totalAmount());
    }

    @Test
    void rejectQuantityGreaterThanOne() {
        UUID petId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 2)));
        Authentication auth = authenticatedUser("7", "USER");

        assertThrows(IllegalArgumentException.class, () -> orderCommandService.create(request, auth));
    }

    @Test
    void rejectMissingPet() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetNotFoundException(petId));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("7", "USER");

        assertThrows(PetNotFoundException.class, () -> orderCommandService.create(request, auth));
    }

    @Test
    void rejectUnavailablePet() {
        UUID petId = UUID.randomUUID();
        PetCatalogPetResponse pet = new PetCatalogPetResponse(
                petId,
                "Max",
                com.petcommerce.order.domain.model.Species.DOG,
                "Golden Retriever",
                BigDecimal.valueOf(75000),
                "SOLD"
        );
        when(petCatalogClient.getPetById(petId)).thenReturn(pet);

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("7", "USER");

        assertThrows(PetUnavailableException.class, () -> orderCommandService.create(request, auth));
    }

    @Test
    void cancelCreatedOrder() {
        Order order = new Order();
        order.setId(12L);
        Long customerId = 7L;
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Authentication auth = authenticatedUser("7", "USER");
        assertDoesNotThrow(() -> orderCommandService.cancel(12L, auth));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void rejectCancellationOfInvalidState() {
        Order order = new Order();
        order.setId(13L);
        Long customerId = 7L;
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(13L)).thenReturn(java.util.Optional.of(order));

        Authentication auth = authenticatedUser("7", "USER");
        assertThrows(InvalidOrderStateException.class, () -> orderCommandService.cancel(13L, auth));
    }

    @Test
    void petCatalogDownReturnsServiceUnavailable() {
        UUID petId = UUID.randomUUID();
        when(petCatalogClient.getPetById(petId)).thenThrow(new PetCatalogUnavailableException("Pet Catalog unavailable"));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(petId, 1)));
        Authentication auth = authenticatedUser("7", "USER");

        assertThrows(PetCatalogUnavailableException.class, () -> orderCommandService.create(request, auth));
    }
}
