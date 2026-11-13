package com.petcommerce.fulfillment.persistence.repository;

import com.petcommerce.fulfillment.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
