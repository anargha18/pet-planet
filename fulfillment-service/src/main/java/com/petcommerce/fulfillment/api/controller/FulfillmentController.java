package com.petcommerce.fulfillment.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.fulfillment.api.dto.FulfillmentResponse;
import com.petcommerce.fulfillment.application.FulfillmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read API for fulfillment records. Requires a valid JWT (security.SecurityConfig); the application service checks that the
 * caller owns the order (or is an admin), so foreign records are rejected with 403 (R-SEC-04).
 */
@RestController
@RequestMapping("/api/v1")
public class FulfillmentController {

    private final FulfillmentService service;

    public FulfillmentController(FulfillmentService service) {
        this.service = service;
    }

    @GetMapping("/fulfillment/order/{orderId}")
    public FulfillmentResponse getByOrderId(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }

    @GetMapping("/fulfillment/customer/{customerId}")
    public List<FulfillmentResponse> getForCustomer(AuthenticatedCaller caller, @PathVariable Long customerId) {
        return service.getForCustomer(customerId, caller);
    }
}
