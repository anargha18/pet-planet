package com.petcommerce.fulfillment.repository;

import com.petcommerce.fulfillment.entity.FulfillmentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FulfillmentOutboxRepository extends JpaRepository<FulfillmentOutboxEvent, UUID> {
}
