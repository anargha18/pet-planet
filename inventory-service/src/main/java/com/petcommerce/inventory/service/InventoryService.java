package com.petcommerce.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.entity.InventoryStatus;
import com.petcommerce.inventory.entity.PetInventory;
import com.petcommerce.inventory.entity.ProcessedEvent;
import com.petcommerce.inventory.event.OrderConfirmedEvent;
import com.petcommerce.inventory.event.OrderCreatedEvent;
import com.petcommerce.inventory.event.PetReservationExpiredEvent;
import com.petcommerce.inventory.event.PetReservationFailedEvent;
import com.petcommerce.inventory.event.PetReservationReleasedEvent;
import com.petcommerce.inventory.event.PetReservedEvent;
import com.petcommerce.inventory.repository.InventoryOutboxRepository;
import com.petcommerce.inventory.repository.PetInventoryRepository;
import com.petcommerce.inventory.repository.ProcessedEventRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    public static final String PET_RESERVED_TOPIC = "pet-reserved-events";
    public static final String PET_RESERVATION_FAILED_TOPIC = "pet-reservation-failed-events";
    public static final String PET_RESERVATION_RELEASED_TOPIC = "pet-reservation-released-events";
    public static final String PET_RESERVATION_EXPIRED_TOPIC = "pet-reservation-expired-events";

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

        for (UUID petId : event.petIds()) {
            PetInventory inventory = petInventoryRepository.findByPetId(petId)
                    .orElseGet(() -> new PetInventory(petId));

            if (inventory.getStatus() == InventoryStatus.AVAILABLE) {
                UUID reservationId = UUID.randomUUID();
                Instant now = Instant.now();
                inventory.setStatus(InventoryStatus.RESERVED);
                inventory.setReservationId(reservationId);
                inventory.setReservedAt(now);
                inventory.setReservationExpiresAt(now.plus(reservationTimeout));
                inventory.setSoldAt(null);
                petInventoryRepository.save(inventory);
                publishAfterCommit(
                        PET_RESERVED_TOPIC,
                        petId.toString(),
                        new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", 1, event.aggregateId(), now, event.correlationId(), event.orderId(), reservationId, petId),
                        "PetReservedEvent",
                        petId,
                        event.orderId(),
                        event.correlationId()
                );
                log.info("Reserved pet {} for order {} with reservation {}", petId, event.orderId(), reservationId);
            } else {
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
            }
        }
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
            log.info("Marked pet {} as SOLD after order confirmation {}", petId, event.orderId());
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
        inventory.setStatus(InventoryStatus.AVAILABLE);
        inventory.setReservationId(null);
        inventory.setReservedAt(null);
        inventory.setReservationExpiresAt(null);
        PetInventory saved = petInventoryRepository.save(inventory);
        publishAfterCommit(
                PET_RESERVATION_RELEASED_TOPIC,
                petId.toString(),
                new PetReservationReleasedEvent(UUID.randomUUID(), "PetReservationReleasedEvent", 1, petId, Instant.now(), UUID.randomUUID(), null, reservationId, petId),
                "PetReservationReleasedEvent",
                petId,
                null,
                null
        );
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
            inventory.setStatus(InventoryStatus.AVAILABLE);
            inventory.setReservationId(null);
            inventory.setReservedAt(null);
            inventory.setReservationExpiresAt(null);
            petInventoryRepository.save(inventory);
            publishAfterCommit(
                    PET_RESERVATION_EXPIRED_TOPIC,
                    inventory.getPetId().toString(),
                    new PetReservationExpiredEvent(UUID.randomUUID(), "PetReservationExpiredEvent", 1, inventory.getPetId(), Instant.now(), UUID.randomUUID(), null, inventory.getPetId()),
                    "PetReservationExpiredEvent",
                    inventory.getPetId(),
                    null,
                    null
            );
            log.info("Reservation expired for pet {}", inventory.getPetId());
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

    private void publishAfterCommit(String topic, String key, Object event, String eventType, UUID petId, Long orderId, UUID correlationId) {
        inventoryOutboxRepository.save(new com.petcommerce.inventory.entity.InventoryOutboxEvent(
                UUID.randomUUID(),
                topic,
                eventType,
                petId,
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, event);
                }
            });
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
