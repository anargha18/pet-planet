package com.petcommerce.delivery.controller;

import com.petcommerce.delivery.entity.DeliveryRecord;
import com.petcommerce.delivery.service.DeliveryTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DeliveryTrackingController {

    private final DeliveryTrackingService deliveryTrackingService;

    public DeliveryTrackingController(DeliveryTrackingService deliveryTrackingService) {
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @GetMapping("/deliveries/order/{orderId}")
    public ResponseEntity<DeliveryRecord> getDeliveryByOrderId(@PathVariable Long orderId) {
        return deliveryTrackingService.getDeliveryByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/deliveries/customer/{customerId}")
    public ResponseEntity<List<DeliveryRecord>> getDeliveriesForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(deliveryTrackingService.getDeliveriesByCustomerId(customerId));
    }

    @PutMapping("/deliveries/order/{orderId}/delivered")
    public ResponseEntity<DeliveryRecord> markDelivered(@PathVariable Long orderId) {
        return ResponseEntity.ok(deliveryTrackingService.markDelivered(orderId));
    }
}
