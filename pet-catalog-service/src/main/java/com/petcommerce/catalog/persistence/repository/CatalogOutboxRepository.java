package com.petcommerce.catalog.persistence.repository;

import com.petcommerce.catalog.persistence.entity.CatalogOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent, UUID> {

    /** Oldest unpublished rows, locked for the relay transaction; rows locked by another relay run are skipped. */
    @Query(value = "SELECT * FROM catalog_outbox WHERE published_at IS NULL ORDER BY created_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<CatalogOutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
