package com.petcommerce.shipping.repository;

import com.petcommerce.shipping.entity.ShipmentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentOutboxRepository extends JpaRepository<ShipmentOutboxEvent, UUID> {
}
