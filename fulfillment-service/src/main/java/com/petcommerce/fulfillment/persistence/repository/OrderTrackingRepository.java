package com.petcommerce.fulfillment.persistence.repository;

import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderTrackingRepository extends JpaRepository<OrderTracking, Long> {

    /** Creates the row if missing without racing other listener threads (no duplicate-key failures). */
    @Modifying
    @Query(value = "INSERT INTO order_tracking (order_id, escalated, created_at, updated_at) "
            + "VALUES (:orderId, FALSE, now(), now()) ON CONFLICT (order_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId);

    /** Row lock: listeners for different topics update the same order concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OrderTracking t where t.orderId = :orderId")
    Optional<OrderTracking> lockByOrderId(@Param("orderId") Long orderId);

    Page<OrderTracking> findByCustomerId(Long customerId, Pageable pageable);

    Page<OrderTracking> findByEscalated(boolean escalated, Pageable pageable);

    Page<OrderTracking> findByCustomerIdAndEscalated(Long customerId, boolean escalated, Pageable pageable);

    List<OrderTracking> findByEscalatedFalseAndOrderStatusInAndOrderStatusAtBefore(
            Collection<TrackedOrderStatus> statuses, Instant before);
}
