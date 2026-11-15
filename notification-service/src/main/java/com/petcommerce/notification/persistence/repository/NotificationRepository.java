package com.petcommerce.notification.persistence.repository;

import com.petcommerce.notification.domain.model.Notification;
import com.petcommerce.notification.domain.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    List<Notification> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Notification> findFirstByOrderId(Long orderId);
    boolean existsByOrderIdAndType(Long orderId, NotificationType type);
}
