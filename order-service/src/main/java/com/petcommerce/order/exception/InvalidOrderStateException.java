package com.petcommerce.order.exception;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(Long orderId, String currentStatus) {
        super("Order " + orderId + " cannot be cancelled because its status is " + currentStatus);
    }
}
