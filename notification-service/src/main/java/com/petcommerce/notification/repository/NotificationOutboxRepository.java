package com.petcommerce.notification.repository;

import com.petcommerce.notification.entity.NotificationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEvent, UUID> {
    List<NotificationOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
