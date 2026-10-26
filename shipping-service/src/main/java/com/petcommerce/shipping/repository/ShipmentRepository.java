package com.petcommerce.shipping.repository;

import com.petcommerce.shipping.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOrderId(Long orderId);
    List<Shipment> findByCustomerId(Long customerId);
}
