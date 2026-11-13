package com.petcommerce.shipping.persistence.repository;

import com.petcommerce.shipping.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
