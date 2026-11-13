package com.petcommerce.delivery.persistence.repository;

import com.petcommerce.delivery.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
