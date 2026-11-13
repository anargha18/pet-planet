package com.petcommerce.order.application;

import com.petcommerce.order.api.dto.OrderResponse;
import com.petcommerce.order.api.mapper.OrderMapper;
import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.persistence.repository.OrderRepository;
import com.petcommerce.order.persistence.repository.OrderSpecifications;
import com.petcommerce.order.security.OrderAccessPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only use cases: view one order, list orders. */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderAccessPolicy orderAccessPolicy;

    public OrderQueryService(OrderRepository orderRepository, OrderAccessPolicy orderAccessPolicy) {
        this.orderRepository = orderRepository;
        this.orderAccessPolicy = orderAccessPolicy;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return getOrder(orderId, null);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, Authentication authentication) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        orderAccessPolicy.checkCanAccess(order, authentication);
        return OrderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Long customerId, OrderStatus status, Pageable pageable) {
        return getOrders(customerId, status, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Long customerId, OrderStatus status, Authentication authentication, Pageable pageable) {
        Long effectiveCustomerId = orderAccessPolicy.resolveListScope(customerId, authentication);
        return orderRepository.findAll(OrderSpecifications.byCustomerAndStatus(effectiveCustomerId, status), pageable)
                .map(OrderMapper::toResponse);
    }
}
