package com.petcommerce.notification.persistence.repository;

import com.petcommerce.notification.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByOrderId(Long orderId);
    List<Notification> findByCustomerId(Long customerId);
}
