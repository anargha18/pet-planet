package com.petcommerce.notification.controller;

import com.petcommerce.notification.entity.Notification;
import com.petcommerce.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications/order/{orderId}")
    public ResponseEntity<Notification> getNotificationByOrderId(@PathVariable Long orderId) {
        return notificationService.getNotificationByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/notifications/customer/{customerId}")
    public ResponseEntity<List<Notification>> getNotificationsForCustomer(@PathVariable Long customerId) {
        List<Notification> notifications = notificationService.getNotificationsByCustomerId(customerId);
        return ResponseEntity.ok(notifications);
    }
}
