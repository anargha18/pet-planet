package com.petcommerce.order.persistence.repository;

import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Query building blocks for {@link OrderRepository}. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    /** Matches orders by optional customer ID and optional status (null = no filter). */
    public static Specification<Order> byCustomerAndStatus(Long customerId, OrderStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
