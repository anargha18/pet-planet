package com.petcommerce.inventory.persistence.repository;

import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryOutboxRepository extends JpaRepository<InventoryOutboxEvent, UUID> {
    List<InventoryOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
