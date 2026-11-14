package com.petcommerce.delivery.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.api.mapper.DeliveryRecordMapper;
import com.petcommerce.delivery.application.DeliveryTrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read API for deliverys. Requires a valid JWT (security.SecurityConfig); the application service checks that the
 * caller owns the order (or is an admin), so foreign records are rejected with 403 (R-SEC-04).
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

    /** Admin only (security.SecurityConfig). */
    @PutMapping("/deliveries/order/{orderId}/delivered")
    public DeliveryResponse markDelivered(@PathVariable Long orderId) {
        return DeliveryRecordMapper.toResponse(service.markDelivered(orderId));
    }
}
