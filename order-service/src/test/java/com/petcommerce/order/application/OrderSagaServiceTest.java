package com.petcommerce.order.application;

import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.messaging.event.PaymentFailedEvent;
import com.petcommerce.order.messaging.event.PaymentSucceededEvent;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderSagaService orderSagaService;

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

        assertDoesNotThrow(() -> orderSagaService.handlePaymentSucceeded(event));
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

        assertDoesNotThrow(() -> orderSagaService.handlePaymentFailed(event));
        assertEquals(OrderStatus.FAILED, order.getStatus());
    }
}
