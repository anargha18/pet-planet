package com.petcommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.dto.CreateOrderItemRequest;
import com.petcommerce.order.dto.CreateOrderRequest;
import com.petcommerce.order.dto.OrderItemResponse;
import com.petcommerce.order.dto.OrderResponse;
import com.petcommerce.order.entity.Order;
import com.petcommerce.order.entity.OrderItem;
import com.petcommerce.order.entity.OrderOutboxEvent;
import com.petcommerce.order.entity.OrderStatus;
import com.petcommerce.order.entity.Species;
import com.petcommerce.order.event.OrderConfirmedEvent;
import com.petcommerce.order.event.PaymentFailedEvent;
import com.petcommerce.order.event.PaymentSucceededEvent;
import com.petcommerce.order.exception.InvalidOrderStateException;
import com.petcommerce.order.exception.OrderNotFoundException;
import com.petcommerce.order.exception.PetUnavailableException;
import com.petcommerce.order.repository.OrderOutboxRepository;
import com.petcommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final PetCatalogClient petCatalogClient;
    private final OrderOutboxRepository orderOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        PetCatalogClient petCatalogClient,
                        OrderOutboxRepository orderOutboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.petCatalogClient = petCatalogClient;
        this.orderOutboxRepository = orderOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
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

        Long customerId = resolveCustomerId(authentication);
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
        log.info("Created order {} for customer {} with total {}", savedOrder.getId(), savedOrder.getCustomerId(), savedOrder.getTotalAmount());
        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return getOrder(orderId, null);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, Authentication authentication) {
        Order order = findOrder(orderId);
        ensureOrderAccess(order, authentication);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Long customerId, OrderStatus status, Pageable pageable) {
        return getOrders(customerId, status, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Long customerId, OrderStatus status, Authentication authentication, Pageable pageable) {
        Long effectiveCustomerId = resolveRequestedCustomerId(customerId, authentication);
        Specification<Order> specification = buildSpecification(effectiveCustomerId, status);
        return orderRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public OrderResponse cancel(Long orderId) {
        return cancel(orderId, null);
    }

    @Transactional
    public OrderResponse cancel(Long orderId, Authentication authentication) {
        Order order = findOrder(orderId);
        ensureOrderAccess(order, authentication);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(orderId, order.getStatus().name());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        log.info("Cancelled order {}", updated.getId());
        return toResponse(updated);
    }

    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already processed for payment result {}", order.getId(), event.paymentId());
            return;
        }

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.warn("Ignoring payment success for order {} in unexpected state {}", order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        publishOrderConfirmedAfterCommit(order, event.correlationId());
        log.info("Order {} marked as PAID after payment {} succeeded", order.getId(), event.paymentId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Ignoring payment failure for order {} in terminal state {}", order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.warn("Order {} marked as FAILED after payment {} failed: {}", order.getId(), event.paymentId(), event.reason());
    }

    private void publishOrderConfirmedAfterCommit(Order order, UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        List<UUID> petIds = order.getItems().stream()
                .map(OrderItem::getPetId)
                .toList();
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                order.getId(),
                petIds,
                order.getTotalAmount()
        );

        orderOutboxRepository.save(new OrderOutboxEvent(
                eventId,
                "order-confirmed-events",
                "OrderConfirmedEvent",
                order.getId(),
                correlationId,
                serializePayload(event),
                Instant.now()
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send("order-confirmed-events", order.getId().toString(), event);
                }
            });
            return;
        }

        kafkaTemplate.send("order-confirmed-events", order.getId().toString(), event);
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize order event", ex);
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity != 1) {
            throw new IllegalArgumentException("quantity must always be 1 for a pet order item");
        }
    }

    private Long resolveCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        return customerIdFromPrincipal(authentication.getName());
    }

    private Long resolveRequestedCustomerId(Long requestedCustomerId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "ADMIN")) {
            return requestedCustomerId;
        }
        Long principalCustomerId = resolveCustomerId(authentication);
        if (requestedCustomerId != null && !requestedCustomerId.equals(principalCustomerId)) {
            throw new AccessDeniedException("You can only list your own orders");
        }
        return principalCustomerId;
    }

    private void ensureOrderAccess(Order order, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "ADMIN")) {
            return;
        }
        if (!hasRole(authentication, "CUSTOMER")) {
            throw new AccessDeniedException("Insufficient permissions");
        }
        Long authenticatedCustomerId = resolveCustomerId(authentication);
        if (!authenticatedCustomerId.equals(order.getCustomerId())) {
            throw new AccessDeniedException("You are not allowed to access this order");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            String authorityName = authority.getAuthority();
            if (("ROLE_" + role).equalsIgnoreCase(authorityName) || role.equalsIgnoreCase(authorityName)) {
                return true;
            }
        }
        return false;
    }

    private Long customerIdFromPrincipal(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new AccessDeniedException("Authentication principal is missing");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(principalName.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, Long.BYTES);
            long value = buffer.getLong();
            return Math.abs(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to derive the authenticated customer identity", ex);
        }
    }

    private Specification<Order> buildSpecification(Long customerId, OrderStatus status) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Species mapSpecies(com.petcommerce.order.entity.Species species) {
        if (species == null) {
            return null;
        }
        return Species.valueOf(species.name());
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getPetId(),
                item.getPetName(),
                item.getPetSpecies(),
                item.getPetBreed(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
