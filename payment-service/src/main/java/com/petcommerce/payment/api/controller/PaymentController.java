package com.petcommerce.payment.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.payment.api.dto.PaymentResponse;
import com.petcommerce.payment.application.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for payments. Requires a valid JWT (security.SecurityConfig); the application service checks that the
 * caller owns the order (or is an admin), so foreign records are rejected with 403 (R-SEC-04).
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/payments/order/{orderId}")
    public PaymentResponse getByOrderId(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }
}
