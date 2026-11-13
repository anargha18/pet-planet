package com.petcommerce.order.domain.model;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    CONFIRMED,
    CANCELLED,
    FAILED
}
