package com.petcommerce.payment.persistence.repository;

import com.petcommerce.payment.domain.model.ClosedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClosedOrderRepository extends JpaRepository<ClosedOrder, Long> {

    /** Records the order as closed; repeated calls keep the first reason. */
    @Modifying
    @Query(value = "INSERT INTO closed_orders (order_id, reason, closed_at) VALUES (:orderId, :reason, now()) "
            + "ON CONFLICT (order_id) DO NOTHING", nativeQuery = true)
    int markClosed(@Param("orderId") Long orderId, @Param("reason") String reason);
}
