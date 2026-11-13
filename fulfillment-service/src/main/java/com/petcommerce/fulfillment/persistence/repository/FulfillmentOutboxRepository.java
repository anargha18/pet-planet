package com.petcommerce.fulfillment.persistence.repository;

import com.petcommerce.fulfillment.persistence.entity.FulfillmentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FulfillmentOutboxRepository extends JpaRepository<FulfillmentOutboxEvent, UUID> {
    List<FulfillmentOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
