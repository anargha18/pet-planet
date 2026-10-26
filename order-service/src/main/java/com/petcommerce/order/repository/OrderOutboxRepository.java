package com.petcommerce.order.repository;

import com.petcommerce.order.entity.OrderOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, UUID> {
}
