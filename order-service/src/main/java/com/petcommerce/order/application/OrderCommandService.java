package com.petcommerce.order.application;

import com.petcommerce.order.api.dto.CreateOrderItemRequest;
import com.petcommerce.order.api.dto.CreateOrderRequest;
import com.petcommerce.order.api.dto.OrderResponse;
import com.petcommerce.order.api.mapper.OrderMapper;
import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.domain.exception.InvalidOrderStateException;
import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.exception.PetUnavailableException;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderItem;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.domain.model.Species;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import com.petcommerce.order.security.OrderAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

/** Use cases that change orders on behalf of a customer: place an order, cancel an order. */
@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final OrderRepository orderRepository;
    private final PetCatalogClient petCatalogClient;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderAccessPolicy orderAccessPolicy;

    public OrderCommandService(OrderRepository orderRepository,
                               PetCatalogClient petCatalogClient,
                               OrderEventPublisher orderEventPublisher,
                               OrderAccessPolicy orderAccessPolicy) {
        this.orderRepository = orderRepository;
        this.petCatalogClient = petCatalogClient;
        this.orderEventPublisher = orderEventPublisher;
        this.orderAccessPolicy = orderAccessPolicy;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return create(request, null);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Authentication authentication) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        Long customerId = orderAccessPolicy.requireCustomerId(authentication);
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setItems(new ArrayList<>());

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.items()) {
            validateQuantity(itemRequest.quantity());
            PetCatalogPetResponse pet = petCatalogClient.getPetById(itemRequest.petId());
            if (pet.availabilityStatus() == null || !"AVAILABLE".equalsIgnoreCase(pet.availabilityStatus())) {
                throw new PetUnavailableException(itemRequest.petId());
            }

            BigDecimal unitPrice = pet.price();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(subtotal);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setPetId(pet.id());
            item.setPetName(pet.name());
            item.setPetSpecies(mapSpecies(pet.species()));
            item.setPetBreed(pet.breed());
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemRequest.quantity());
            item.setSubtotal(subtotal);
            order.getItems().add(item);
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(savedOrder);
        log.info("Created order {} for customer {} with total {} and queued OrderCreatedEvent",
                savedOrder.getId(), savedOrder.getCustomerId(), savedOrder.getTotalAmount());
        return OrderMapper.toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse cancel(Long orderId) {
        return cancel(orderId, null);
    }

    @Transactional
    public OrderResponse cancel(Long orderId, Authentication authentication) {
        Order order = findOrder(orderId);
        orderAccessPolicy.checkCanAccess(order, authentication);
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(orderId, order.getStatus().name());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        orderEventPublisher.publishOrderCancelled(updated);
        log.info("Cancelled order {} and queued OrderCancelledEvent", updated.getId());
        return OrderMapper.toResponse(updated);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity != 1) {
            throw new IllegalArgumentException("quantity must always be 1 for a pet order item");
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Species mapSpecies(Species species) {
        if (species == null) {
            return null;
        }
        return Species.valueOf(species.name());
    }
}
