package com.petcommerce.order.domain.policy;

import com.petcommerce.order.domain.model.OrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single place that decides which order status changes are legal (R-SAGA-01).
 *
 * <pre>
 * CREATED ──PetReserved──▶ PAYMENT_PENDING ──PetsSold──▶ CONFIRMED
 *    │                          │
 *    │ ReservationFailed        │ PaymentFailed / SaleConflict            → FAILED
 *    │ Customer cancel          │ Customer cancel / ReservationExpired    → CANCELLED
 * </pre>
 * CONFIRMED, CANCELLED and FAILED are terminal. CREATED may jump straight to CONFIRMED because events from
 * different topics can arrive out of order (PetsSold before PetReserved).
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.CREATED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return from != null && ALLOWED.get(from).contains(to);
    }

    public static boolean isTerminal(OrderStatus status) {
        return ALLOWED.get(status).isEmpty();
    }
}
