package com.petcommerce.notification.persistence.repository;

import com.petcommerce.notification.persistence.entity.NotificationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEvent, UUID> {
    /** Oldest unpublished rows, locked for the relay transaction; rows locked by another relay run are skipped. */
    @Query(value = "SELECT * FROM notification_outbox WHERE published_at IS NULL ORDER BY created_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationOutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
