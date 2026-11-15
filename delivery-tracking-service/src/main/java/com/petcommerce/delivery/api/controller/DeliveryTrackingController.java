package com.petcommerce.delivery.api.controller;

import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.api.dto.UpdateDeliveryStatusRequest;
import com.petcommerce.delivery.application.DeliveryTrackingService;
import com.petcommerce.platform.security.AuthenticatedCaller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Delivery API. Reads require a valid JWT and the application service checks that the caller owns the order (or is
 * an admin), so foreign records are rejected with 403 (R-SEC-04). Advancing a delivery is the single admin
 * "delivery progress" action in the system (R-SAGA-06).
 */
@RestController
@RequestMapping("/api/v1")
public class DeliveryTrackingController {

    private final DeliveryTrackingService service;

    public DeliveryTrackingController(DeliveryTrackingService service) {
        this.service = service;
    }

    @GetMapping("/deliveries/order/{orderId}")
    public DeliveryResponse getByOrderId(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }

    @GetMapping("/deliveries/customer/{customerId}")
    public List<DeliveryResponse> getForCustomer(AuthenticatedCaller caller, @PathVariable Long customerId) {
        return service.getForCustomer(customerId, caller);
    }

    /** Admin only (security.SecurityConfig): 404 unknown order, 409 illegal transition, 400 bad body. */
    @PutMapping("/deliveries/order/{orderId}/status")
    public DeliveryResponse updateStatus(@PathVariable Long orderId, @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return service.advance(orderId, request.status(), request.note());
    }
}
