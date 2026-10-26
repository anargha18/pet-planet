package com.petcommerce.shipping.controller;

import com.petcommerce.shipping.entity.Shipment;
import com.petcommerce.shipping.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @GetMapping("/shipments/order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(@PathVariable Long orderId) {
        return shippingService.getShipmentByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/shipments/customer/{customerId}")
    public ResponseEntity<List<Shipment>> getShipmentsForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(shippingService.getShipmentsByCustomerId(customerId));
    }
}
