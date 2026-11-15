package com.petcommerce.shipping.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.shipping.api.dto.ShipmentResponse;
import com.petcommerce.shipping.application.ShippingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only API for shipments (R-SVC-03: no mutation endpoint; status mirrors delivery-tracking events). Requires a valid JWT (security.SecurityConfig); the application service checks that the
 * caller owns the order (or is an admin), so foreign records are rejected with 403 (R-SEC-04).
 */
@RestController
@RequestMapping("/api/v1")
public class ShippingController {

    private final ShippingService service;

    public ShippingController(ShippingService service) {
        this.service = service;
    }

    @GetMapping("/shipments/order/{orderId}")
    public ShipmentResponse getByOrderId(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }

    @GetMapping("/shipments/customer/{customerId}")
    public List<ShipmentResponse> getForCustomer(AuthenticatedCaller caller, @PathVariable Long customerId) {
        return service.getForCustomer(customerId, caller);
    }
}
