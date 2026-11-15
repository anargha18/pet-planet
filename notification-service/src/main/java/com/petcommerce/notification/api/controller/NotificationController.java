package com.petcommerce.notification.api.controller;

import com.petcommerce.notification.api.dto.NotificationResponse;
import com.petcommerce.notification.application.NotificationService;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read API for notifications (R-SAGA-09: lists per order and per customer). Requires a valid JWT; the application
 * service checks ownership, so another customer's notifications are rejected with 403 (R-SEC-04).
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications/order/{orderId}")
    public List<NotificationResponse> getForOrder(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }

    @GetMapping("/notifications/customer/{customerId}")
    public List<NotificationResponse> getForCustomer(AuthenticatedCaller caller, @PathVariable Long customerId) {
        return service.getForCustomer(customerId, caller);
    }

    @GetMapping("/notifications/me")
    public List<NotificationResponse> getMine(AuthenticatedCaller caller) {
        return service.getForCustomer(caller.userId(), caller);
    }
}
