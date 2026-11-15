package com.petcommerce.delivery.persistence.repository;

import com.petcommerce.delivery.domain.model.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findByOrderIdOrderByOccurredAtAsc(Long orderId);
    List<DeliveryEvent> findByOrderIdInOrderByOccurredAtAsc(Collection<Long> orderIds);
}
