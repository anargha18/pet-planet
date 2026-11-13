package com.petcommerce.payment.persistence.repository;

import com.petcommerce.payment.persistence.entity.PaymentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    List<PaymentOutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
