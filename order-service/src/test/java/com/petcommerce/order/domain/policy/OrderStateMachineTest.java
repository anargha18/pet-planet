package com.petcommerce.order.domain.policy;

import com.petcommerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.petcommerce.order.domain.model.OrderStatus.CANCELLED;
import static com.petcommerce.order.domain.model.OrderStatus.CONFIRMED;
import static com.petcommerce.order.domain.model.OrderStatus.CREATED;
import static com.petcommerce.order.domain.model.OrderStatus.FAILED;
import static com.petcommerce.order.domain.model.OrderStatus.PAYMENT_PENDING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertTrue(OrderStateMachine.canTransition(CREATED, PAYMENT_PENDING));
        assertTrue(OrderStateMachine.canTransition(PAYMENT_PENDING, CONFIRMED));
        assertTrue(OrderStateMachine.canTransition(CREATED, CONFIRMED), "PetsSold may arrive before PetReserved");
    }

    @Test
    void openOrdersCanBeCancelledOrFailed() {
        for (OrderStatus open : new OrderStatus[]{CREATED, PAYMENT_PENDING}) {
            assertTrue(OrderStateMachine.canTransition(open, CANCELLED));
            assertTrue(OrderStateMachine.canTransition(open, FAILED));
        }
    }

    @Test
    void paymentPendingNeverGoesBackToCreated() {
        assertFalse(OrderStateMachine.canTransition(PAYMENT_PENDING, CREATED));
        assertFalse(OrderStateMachine.canTransition(CONFIRMED, PAYMENT_PENDING));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "CANCELLED", "FAILED"})
    void terminalStatesAllowNoTransition(OrderStatus terminal) {
        assertTrue(OrderStateMachine.isTerminal(terminal));
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(OrderStateMachine.canTransition(terminal, next), terminal + " -> " + next);
        }
    }
}
