package com.petcommerce.order.application;

import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.persistence.repository.OrderRepository;
import com.petcommerce.order.security.OrderAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Spy
    private OrderAccessPolicy orderAccessPolicy = new OrderAccessPolicy();

    @InjectMocks
    private OrderQueryService orderQueryService;

    private Authentication authenticatedUser(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, "pw", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void getOrder() {
        Order order = new Order();
        order.setId(10L);
        Long customerId = 7L;
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.valueOf(75000));
        when(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order));

        Authentication auth = authenticatedUser("7", "USER");
        assertDoesNotThrow(() -> orderQueryService.getOrder(10L, auth));
    }

    @Test
    void orderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        Authentication auth = authenticatedUser("7", "USER");
        assertThrows(RuntimeException.class, () -> orderQueryService.getOrder(99L, auth));
    }

    @Test
    void customerCannotAccessAnotherCustomersOrder() {
        Order otherCustomerOrder = new Order();
        otherCustomerOrder.setId(14L);
        otherCustomerOrder.setCustomerId(8L);
        otherCustomerOrder.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(14L)).thenReturn(java.util.Optional.of(otherCustomerOrder));

        Authentication auth = authenticatedUser("7", "USER");
        assertThrows(AccessDeniedException.class, () -> orderQueryService.getOrder(14L, auth));
    }
}
