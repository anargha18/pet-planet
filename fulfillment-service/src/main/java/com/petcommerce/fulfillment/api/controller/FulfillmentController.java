package com.petcommerce.fulfillment.api.controller;

import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import com.petcommerce.fulfillment.application.FulfillmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/fulfillment/order/{orderId}")
    public ResponseEntity<FulfillmentRecord> getFulfillmentByOrderId(@PathVariable Long orderId) {
        return fulfillmentService.getFulfillmentByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/fulfillment/customer/{customerId}")
    public ResponseEntity<List<FulfillmentRecord>> getFulfillmentsForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(fulfillmentService.getFulfillmentsByCustomerId(customerId));
    }
}
