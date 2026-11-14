package com.petcommerce.order.application;

import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.messaging.event.InventorySaleConflictEvent;
import com.petcommerce.order.messaging.event.PaymentFailedEvent;
import com.petcommerce.order.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.order.messaging.event.PetReservedEvent;
import com.petcommerce.order.messaging.event.PetsSoldEvent;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderSagaService orderSagaService;

    private Order order(long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(7L);
        order.setStatus(status);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void petsSoldConfirmsOrderAndPublishesOrderConfirmed() {
        Order order = order(20L, OrderStatus.PAYMENT_PENDING);
        UUID correlationId = UUID.randomUUID();

        orderSagaService.handlePetsSold(new PetsSoldEvent(UUID.randomUUID(), "PetsSoldEvent", 1, UUID.randomUUID(),
                Instant.now(), correlationId, 20L, List.of(UUID.randomUUID())));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderEventPublisher).publishOrderConfirmed(order, correlationId);
    }

    @Test
    void paymentFailureFailsOrderAndPublishesOrderFailed() {
        Order order = order(21L, OrderStatus.PAYMENT_PENDING);
        UUID correlationId = UUID.randomUUID();

        orderSagaService.handlePaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), "PaymentFailedEvent", 1,
                UUID.randomUUID(), Instant.now(), correlationId, 21L, UUID.randomUUID(), BigDecimal.TEN, "PAYMENT_DECLINED"));

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderEventPublisher).publishOrderFailed(order, correlationId, "PAYMENT_FAILED:PAYMENT_DECLINED");
    }

    @Test
    void reservationExpiryCancelsOrderAndPublishesOrderCancelled() {
        Order order = order(22L, OrderStatus.PAYMENT_PENDING);

        orderSagaService.handlePetReservationExpired(new PetReservationExpiredEvent(UUID.randomUUID(),
                "PetReservationExpiredEvent", 1, UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 22L, UUID.randomUUID()));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderEventPublisher).publishOrderCancelled(order, "RESERVATION_EXPIRED");
    }

    @Test
    void saleConflictFailsOrder() {
        Order order = order(23L, OrderStatus.PAYMENT_PENDING);

        orderSagaService.handleSaleConflict(new InventorySaleConflictEvent(UUID.randomUUID(), "InventorySaleConflictEvent",
                1, UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 23L, "PETS_NO_LONGER_RESERVED"));

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderEventPublisher).publishOrderFailed(eq(order), any(), eq("SALE_CONFLICT:PETS_NO_LONGER_RESERVED"));
    }

    @Test
    void lateSuccessForCancelledOrderChangesNothing() {
        Order order = order(24L, OrderStatus.CANCELLED);

        orderSagaService.handlePetsSold(new PetsSoldEvent(UUID.randomUUID(), "PetsSoldEvent", 1, UUID.randomUUID(),
                Instant.now(), UUID.randomUUID(), 24L, List.of()));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderEventPublisher, never()).publishOrderConfirmed(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void reservationAfterConfirmationIsIgnored() {
        Order order = order(25L, OrderStatus.CONFIRMED);

        orderSagaService.handlePetReserved(new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", 1,
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 25L, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderEventPublisher, never()).publishOrderFailed(any(), any(), anyString());
    }
}
