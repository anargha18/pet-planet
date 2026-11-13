package com.petcommerce.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import com.petcommerce.inventory.domain.model.InventoryStatus;
import com.petcommerce.inventory.domain.model.PetInventory;
import com.petcommerce.inventory.persistence.entity.ProcessedEvent;
import com.petcommerce.inventory.messaging.event.InventoryStatusChangedEvent;
import com.petcommerce.inventory.messaging.event.OrderCancelledEvent;
import com.petcommerce.inventory.messaging.event.OrderConfirmedEvent;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import com.petcommerce.inventory.messaging.event.PaymentFailedEvent;
import com.petcommerce.inventory.messaging.event.PetCatalogEvent;
import com.petcommerce.inventory.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.inventory.messaging.event.PetReservationFailedEvent;
import com.petcommerce.inventory.messaging.event.PetReservationReleasedEvent;
import com.petcommerce.inventory.messaging.event.PetReservedEvent;
import com.petcommerce.inventory.persistence.repository.InventoryOutboxRepository;
import com.petcommerce.inventory.persistence.repository.PetInventoryRepository;
import com.petcommerce.inventory.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    public static final String PET_RESERVED_TOPIC = "pet-reserved-events";
    public static final String PET_RESERVATION_FAILED_TOPIC = "pet-reservation-failed-events";
    public static final String PET_RESERVATION_RELEASED_TOPIC = "pet-reservation-released-events";
    public static final String PET_RESERVATION_EXPIRED_TOPIC = "pet-reservation-expired-events";
    public static final String INVENTORY_STATUS_TOPIC = "inventory-status-events";

    private final PetInventoryRepository petInventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration reservationTimeout;

    public InventoryService(
            PetInventoryRepository petInventoryRepository,
            ProcessedEventRepository processedEventRepository,
            InventoryOutboxRepository inventoryOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            org.springframework.core.env.Environment environment) {
        this.petInventoryRepository = petInventoryRepository;
        this.processedEventRepository = processedEventRepository;
        this.inventoryOutboxRepository = inventoryOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
        this.reservationTimeout = Duration.ofMinutes(Integer.parseInt(environment.getProperty("inventory.reservation-expiration-minutes", "5")));
    }

    @Transactional
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        if (event == null || event.petIds() == null || event.petIds().isEmpty()) {
            return;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed event {}", event.eventId());
            return;
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event {} detected and ignored", event.eventId());
            return;
        }

        // First verify all requested pets in the order are AVAILABLE (atomic check)
        List<PetInventory> targetInventories = new ArrayList<>();
        for (UUID petId : event.petIds()) {
            PetInventory inventory = petInventoryRepository.findByPetId(petId)
                    .orElseGet(() -> new PetInventory(petId));
            if (inventory.getStatus() != InventoryStatus.AVAILABLE) {
                String reason = switch (inventory.getStatus()) {
                    case SOLD -> "PET_ALREADY_SOLD";
                    case RESERVED -> "PET_ALREADY_RESERVED";
                    default -> "PET_NOT_FOUND";
                };
                log.warn("Pet {} is unavailable for order {}. Reason: {}", petId, event.orderId(), reason);
                publishAfterCommit(
                        PET_RESERVATION_FAILED_TOPIC,
                        petId.toString(),
                        new PetReservationFailedEvent(UUID.randomUUID(), "PetReservationFailedEvent", 1, event.aggregateId(), Instant.now(), event.correlationId(), event.orderId(), petId, reason),
                        "PetReservationFailedEvent",
                        petId,
                        event.orderId(),
                        event.correlationId()
                );
                return;
            }
            targetInventories.add(inventory);
        }

        UUID orderReservationId = UUID.randomUUID();
        Instant now = Instant.now();
        for (PetInventory inventory : targetInventories) {
            inventory.setStatus(InventoryStatus.RESERVED);
            inventory.setOrderId(event.orderId());
            inventory.setReservationId(orderReservationId);
            inventory.setReservedAt(now);
            inventory.setReservationExpiresAt(now.plus(reservationTimeout));
            inventory.setSoldAt(null);
            petInventoryRepository.save(inventory);
            publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.RESERVED, event.orderId(), event.correlationId());
            log.info("Reserved pet {} for order {} with reservation {}", inventory.getPetId(), event.orderId(), orderReservationId);
        }

        UUID primaryPetId = event.petIds().get(0);
        publishAfterCommit(
                PET_RESERVED_TOPIC,
                primaryPetId.toString(),
                new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", 1, event.aggregateId(), now, event.correlationId(), event.orderId(), orderReservationId, primaryPetId, event.totalAmount()),
                "PetReservedEvent",
                primaryPetId,
                event.orderId(),
                event.correlationId()
        );
    }

    @Transactional
    public void handleOrderConfirmedEvent(OrderConfirmedEvent event) {
        if (event == null || event.petIds() == null || event.petIds().isEmpty()) {
            return;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed order-confirmed event {}", event.eventId());
            return;
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate order-confirmed event {} detected and ignored", event.eventId());
            return;
        }

        for (UUID petId : event.petIds()) {
            PetInventory inventory = petInventoryRepository.findByPetId(petId).orElse(null);
            if (inventory == null) {
                log.warn("No inventory row found for confirmed pet {} from order {}", petId, event.orderId());
                continue;
            }

            if (inventory.getStatus() == InventoryStatus.SOLD) {
                log.info("Pet {} already marked sold for order {}", petId, event.orderId());
                continue;
            }

            markSold(petId);
            publishCatalogStatusSync(petId, InventoryStatus.SOLD, event.orderId(), event.correlationId());
            log.info("Marked pet {} as SOLD after order confirmation {}", petId, event.orderId());
        }
    }

    @Transactional
    public void handlePaymentFailedEvent(PaymentFailedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (event.eventId() != null) {
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
            } catch (DataIntegrityViolationException ex) {
                return;
            }
        }
        releaseOrderReservations(event.orderId(), event.correlationId());
    }

    @Transactional
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (event.eventId() != null) {
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
            } catch (DataIntegrityViolationException ex) {
                return;
            }
        }
        releaseOrderReservations(event.orderId(), event.correlationId());
    }

    @Transactional
    public void handlePetCatalogEvent(PetCatalogEvent event) {
        if (event == null || event.petId() == null) {
            return;
        }
        if (petInventoryRepository.findByPetId(event.petId()).isEmpty()) {
            petInventoryRepository.save(new PetInventory(event.petId()));
            log.info("Initialized inventory record for newly created catalog pet {}", event.petId());
        }
    }

    private void releaseOrderReservations(Long orderId, UUID correlationId) {
        List<PetInventory> reservedForOrder = petInventoryRepository.findByOrderId(orderId);
        for (PetInventory inventory : reservedForOrder) {
            if (inventory.getStatus() == InventoryStatus.RESERVED) {
                UUID reservationId = inventory.getReservationId();
                inventory.setStatus(InventoryStatus.AVAILABLE);
                inventory.setOrderId(null);
                inventory.setReservationId(null);
                inventory.setReservedAt(null);
                inventory.setReservationExpiresAt(null);
                petInventoryRepository.save(inventory);
                publishAfterCommit(
                        PET_RESERVATION_RELEASED_TOPIC,
                        inventory.getPetId().toString(),
                        new PetReservationReleasedEvent(UUID.randomUUID(), "PetReservationReleasedEvent", 1, inventory.getPetId(), Instant.now(), correlationId, orderId, reservationId, inventory.getPetId()),
                        "PetReservationReleasedEvent",
                        inventory.getPetId(),
                        orderId,
                        correlationId
                );
                publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.AVAILABLE, orderId, correlationId);
                log.info("Released reservation for pet {} on order {}", inventory.getPetId(), orderId);
            }
        }
    }

    @Transactional
    public PetInventory reservePet(UUID petId, UUID reservationId) {
        PetInventory inventory = petInventoryRepository.findByPetId(petId)
                .orElseGet(() -> new PetInventory(petId));

        if (inventory.getStatus() != InventoryStatus.AVAILABLE) {
            throw new IllegalStateException("Pet " + petId + " is not available for reservation.");
        }

        return reserveInventory(inventory, reservationId);
    }

    @Transactional
    public PetInventory releaseReservation(UUID petId) {
        PetInventory inventory = petInventoryRepository.findByPetId(petId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory entry not found for pet " + petId));

        if (inventory.getStatus() != InventoryStatus.RESERVED) {
            throw new IllegalStateException("Pet " + petId + " is not currently reserved.");
        }

        UUID reservationId = inventory.getReservationId();
        Long orderId = inventory.getOrderId();
        inventory.setStatus(InventoryStatus.AVAILABLE);
        inventory.setOrderId(null);
        inventory.setReservationId(null);
        inventory.setReservedAt(null);
        inventory.setReservationExpiresAt(null);
        PetInventory saved = petInventoryRepository.save(inventory);
        publishAfterCommit(
                PET_RESERVATION_RELEASED_TOPIC,
                petId.toString(),
                new PetReservationReleasedEvent(UUID.randomUUID(), "PetReservationReleasedEvent", 1, petId, Instant.now(), UUID.randomUUID(), orderId, reservationId, petId),
                "PetReservationReleasedEvent",
                petId,
                orderId,
                null
        );
        publishCatalogStatusSync(petId, InventoryStatus.AVAILABLE, orderId, null);
        return saved;
    }

    @Transactional
    public PetInventory markSold(UUID petId) {
        PetInventory inventory = petInventoryRepository.findByPetId(petId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory entry not found for pet " + petId));

        if (inventory.getStatus() == InventoryStatus.SOLD) {
            return inventory;
        }

        inventory.setStatus(InventoryStatus.SOLD);
        inventory.setSoldAt(Instant.now());
        inventory.setReservationId(null);
        inventory.setReservedAt(null);
        inventory.setReservationExpiresAt(null);
        return petInventoryRepository.save(inventory);
    }

    @Scheduled(fixedDelayString = "${inventory.expiration-check-delay-ms:30000}")
    @Transactional
    public void expireReservations() {
        Instant now = Instant.now();
        List<PetInventory> expiredReservations = petInventoryRepository.findByStatusAndReservationExpiresAtBefore(InventoryStatus.RESERVED, now);
        for (PetInventory inventory : expiredReservations) {
            Long expiredOrderId = inventory.getOrderId();
            inventory.setStatus(InventoryStatus.AVAILABLE);
            inventory.setOrderId(null);
            inventory.setReservationId(null);
            inventory.setReservedAt(null);
            inventory.setReservationExpiresAt(null);
            petInventoryRepository.save(inventory);
            publishAfterCommit(
                    PET_RESERVATION_EXPIRED_TOPIC,
                    inventory.getPetId().toString(),
                    new PetReservationExpiredEvent(UUID.randomUUID(), "PetReservationExpiredEvent", 1, inventory.getPetId(), Instant.now(), UUID.randomUUID(), expiredOrderId, inventory.getPetId()),
                    "PetReservationExpiredEvent",
                    inventory.getPetId(),
                    expiredOrderId,
                    null
            );
            publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.AVAILABLE, expiredOrderId, null);
            log.info("Reservation expired for pet {} (order {})", inventory.getPetId(), expiredOrderId);
        }
    }

    private PetInventory reserveInventory(PetInventory inventory, UUID reservationId) {
        Instant now = Instant.now();
        inventory.setStatus(InventoryStatus.RESERVED);
        inventory.setReservationId(reservationId);
        inventory.setReservedAt(now);
        inventory.setReservationExpiresAt(now.plus(reservationTimeout));
        inventory.setSoldAt(null);
        try {
            return petInventoryRepository.save(inventory);
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException("Pet " + inventory.getPetId() + " was already reserved by another transaction.", ex);
        }
    }

    private void publishCatalogStatusSync(UUID petId, InventoryStatus status, Long orderId, UUID correlationId) {
        InventoryStatusChangedEvent statusEvent = new InventoryStatusChangedEvent(
                UUID.randomUUID(),
                "InventoryStatusChangedEvent",
                1,
                petId,
                status.name(),
                orderId,
                Instant.now()
        );
        publishAfterCommit(
                INVENTORY_STATUS_TOPIC,
                petId.toString(),
                statusEvent,
                "InventoryStatusChangedEvent",
                petId,
                orderId,
                correlationId
        );
    }

    private void publishAfterCommit(String topic, String key, Object event, String eventType, UUID petId, Long orderId, UUID correlationId) {
        InventoryOutboxEvent outbox = new InventoryOutboxEvent(
                UUID.randomUUID(),
                topic,
                eventType,
                petId,
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        );
        inventoryOutboxRepository.save(outbox);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAndMarkPublished(outbox, key, event);
                }
            });
            return;
        }
        sendAndMarkPublished(outbox, key, event);
    }

    private void sendAndMarkPublished(InventoryOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        inventoryOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred inventory outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${inventory.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<InventoryOutboxEvent> pending = inventoryOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (InventoryOutboxEvent outbox : pending) {
            try {
                Object payloadObj = objectMapper.readTree(outbox.getPayload());
                String key = outbox.getPetId() != null ? outbox.getPetId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                inventoryOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay inventory outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize inventory event", e);
        }
    }
}
