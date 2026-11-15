package com.petcommerce.payment.persistence.repository;

import com.petcommerce.payment.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /**
     * Serializes all payment work for one order inside the current transaction (PostgreSQL advisory lock, released
     * at commit). A charge and a cancellation for the same order therefore never interleave.
     */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:orderId) AS text)", nativeQuery = true)
    String lockOrder(@Param("orderId") long orderId);
}
