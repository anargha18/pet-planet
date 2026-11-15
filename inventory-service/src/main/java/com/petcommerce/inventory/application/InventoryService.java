package com.petcommerce.inventory.application;

import com.petcommerce.inventory.domain.exception.PetInventoryNotFoundException;
import com.petcommerce.inventory.domain.model.InventoryStatus;
import com.petcommerce.inventory.domain.model.PetInventory;
import com.petcommerce.inventory.messaging.event.InventorySaleConflictEvent;
import com.petcommerce.inventory.messaging.event.InventoryStatusChangedEvent;
import com.petcommerce.inventory.messaging.event.OrderCancelledEvent;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import com.petcommerce.inventory.messaging.event.OrderFailedEvent;
import com.petcommerce.inventory.messaging.event.PaymentSucceededEvent;
import com.petcommerce.inventory.messaging.event.PetCatalogEvent;
import com.petcommerce.inventory.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.inventory.messaging.event.PetReservationFailedEvent;
import com.petcommerce.inventory.messaging.event.PetReservedEvent;
import com.petcommerce.inventory.messaging.event.PetsSoldEvent;
import com.petcommerce.inventory.messaging.publisher.InventoryEventPublisher;
import com.petcommerce.inventory.persistence.repository.PetInventoryRepository;
import com.petcommerce.inventory.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inventory is the single source of truth for whether each unique pet is AVAILABLE, RESERVED, SOLD or RETIRED.
 *
 * <ul>
 *   <li>OrderCreated: reserve all pets of the order or none (unknown pets fail with PET_NOT_FOUND).</li>
 *   <li>PaymentSucceeded: mark the pets SOLD only if they are still reserved for that order, then emit PetsSold;
 *       otherwise emit InventorySaleConflict (fixes the double-sale race, S1).</li>
 *   <li>OrderCancelled / OrderFailed: release the order's reservations (single compensation trigger).</li>
 *   <li>Expired reservations are released by a scheduled job and reported with PetReservationExpired.</li>
 * </ul>
 * Every status change is mirrored to the catalog through inventory-status-events.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    public static final String PET_RESERVED_TOPIC = "pet-reserved-events";
    public static final String PET_RESERVATION_FAILED_TOPIC = "pet-reservation-failed-events";
    public static final String PET_RESERVATION_EXPIRED_TOPIC = "pet-reservation-expired-events";
    public static final String PETS_SOLD_TOPIC = "pets-sold-events";
    public static final String SALE_CONFLICT_TOPIC = "inventory-sale-conflict-events";
    public static final String INVENTORY_STATUS_TOPIC = "inventory-status-events";

    private final PetInventoryRepository petInventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryEventPublisher eventPublisher;
    private final Duration reservationTimeout;

    public InventoryService(
            PetInventoryRepository petInventoryRepository,
            ProcessedEventRepository processedEventRepository,
            InventoryEventPublisher eventPublisher,
            @Value("${inventory.reservation-timeout:PT5M}") Duration reservationTimeout) {
        this.petInventoryRepository = petInventoryRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
        this.reservationTimeout = reservationTimeout;
    }

    @Transactional
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        if (event == null || event.petIds() == null || event.petIds().isEmpty()) {
            return;
        }

        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed event {}", event.eventId());
            return;
        }

        // First verify all requested pets in the order are AVAILABLE (all-or-nothing reservation).
        List<PetInventory> targetInventories = new ArrayList<>();
        for (UUID petId : event.petIds()) {
            PetInventory inventory = petInventoryRepository.findByPetId(petId).orElse(null);
            String reason = inventory == null ? "PET_NOT_FOUND" : switch (inventory.getStatus()) {
                case AVAILABLE -> null;
                case SOLD -> "PET_ALREADY_SOLD";
                case RESERVED -> "PET_ALREADY_RESERVED";
                case RETIRED -> "PET_RETIRED";
            };
            if (reason != null) {
                log.warn("Pet {} is unavailable for order {}. Reason: {}", petId, event.orderId(), reason);
                eventPublisher.publish(
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
        eventPublisher.publish(
                PET_RESERVED_TOPIC,
                primaryPetId.toString(),
                new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", 1, event.aggregateId(), now, event.correlationId(), event.orderId(), orderReservationId, primaryPetId, event.totalAmount(), event.customerId()),
                "PetReservedEvent",
                primaryPetId,
                event.orderId(),
                event.correlationId()
        );
    }

    /**
     * Completes the sale. Pets are sold only while they are still RESERVED for this order; if the reservation
     * expired or was released in the meantime, nothing is sold and a sale conflict is reported instead
     * (the order then fails and payment refunds).
     */
    @Transactional
    public void handlePaymentSucceededEvent(PaymentSucceededEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed payment-succeeded event {}", event.eventId());
            return;
        }

        List<PetInventory> reserved = petInventoryRepository.findByOrderId(event.orderId()).stream()
                .filter(inventory -> inventory.getStatus() == InventoryStatus.RESERVED)
                .toList();
        if (reserved.isEmpty()) {
            log.warn("Payment succeeded for order {} but its pets are no longer reserved; reporting sale conflict", event.orderId());
            eventPublisher.publish(
                    SALE_CONFLICT_TOPIC,
                    event.orderId().toString(),
                    new InventorySaleConflictEvent(UUID.randomUUID(), "InventorySaleConflictEvent", 1, event.aggregateId(), Instant.now(), event.correlationId(), event.orderId(), "PETS_NO_LONGER_RESERVED"),
                    "InventorySaleConflictEvent",
                    null,
                    event.orderId(),
                    event.correlationId()
            );
            return;
        }

        Instant now = Instant.now();
        List<UUID> soldPetIds = new ArrayList<>();
        for (PetInventory inventory : reserved) {
            inventory.setStatus(InventoryStatus.SOLD);
            inventory.setSoldAt(now);
            inventory.setReservationId(null);
            inventory.setReservedAt(null);
            inventory.setReservationExpiresAt(null);
            petInventoryRepository.save(inventory);
            soldPetIds.add(inventory.getPetId());
            publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.SOLD, event.orderId(), event.correlationId());
            log.info("Marked pet {} as SOLD for paid order {}", inventory.getPetId(), event.orderId());
        }
        eventPublisher.publish(
                PETS_SOLD_TOPIC,
                event.orderId().toString(),
                new PetsSoldEvent(UUID.randomUUID(), "PetsSoldEvent", 1, event.aggregateId(), now, event.correlationId(), event.orderId(), soldPetIds),
                "PetsSoldEvent",
                null,
                event.orderId(),
                event.correlationId()
        );
    }

    @Transactional
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (event.eventId() != null && processedEventRepository.markProcessed(event.eventId()) == 0) {
            return;
        }
        releaseOrderReservations(event.orderId(), event.correlationId());
    }

    @Transactional
    public void handleOrderFailedEvent(OrderFailedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (event.eventId() != null && processedEventRepository.markProcessed(event.eventId()) == 0) {
            return;
        }
        releaseOrderReservations(event.orderId(), event.correlationId());
    }

    /** PetCreatedEvent creates the inventory row; PetArchivedEvent retires an AVAILABLE pet. */
    @Transactional
    public void handlePetCatalogEvent(PetCatalogEvent event) {
        if (event == null || event.petId() == null) {
            return;
        }
        PetInventory inventory = petInventoryRepository.findByPetId(event.petId()).orElse(null);
        if ("PetArchivedEvent".equals(event.eventType())) {
            if (inventory != null && inventory.getStatus() == InventoryStatus.AVAILABLE) {
                inventory.setStatus(InventoryStatus.RETIRED);
                petInventoryRepository.save(inventory);
                publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.RETIRED, null, null);
                log.info("Retired archived catalog pet {}", event.petId());
            } else {
                log.warn("Ignoring archive of pet {} in state {}", event.petId(), inventory == null ? "UNKNOWN" : inventory.getStatus());
            }
            return;
        }
        if (inventory == null) {
            petInventoryRepository.save(new PetInventory(event.petId()));
            log.info("Initialized inventory record for newly created catalog pet {}", event.petId());
        }
    }

    /**
     * Releases only pets still RESERVED for this order; pets sold or re-reserved by another order are untouched.
     * The release is announced on inventory-status-events (pet-reservation-released-events was retired in Phase 4).
     */
    private void releaseOrderReservations(Long orderId, UUID correlationId) {
        List<PetInventory> reservedForOrder = petInventoryRepository.findByOrderId(orderId);
        for (PetInventory inventory : reservedForOrder) {
            if (inventory.getStatus() == InventoryStatus.RESERVED) {
                inventory.setStatus(InventoryStatus.AVAILABLE);
                inventory.setOrderId(null);
                inventory.setReservationId(null);
                inventory.setReservedAt(null);
                inventory.setReservationExpiresAt(null);
                petInventoryRepository.save(inventory);
                publishCatalogStatusSync(inventory.getPetId(), InventoryStatus.AVAILABLE, orderId, correlationId);
                log.info("Released reservation for pet {} on order {}", inventory.getPetId(), orderId);
            }
        }
    }

    /** Current inventory state; an unknown pet is a 404 (R-SEC-06), not a made-up AVAILABLE row. */
    @Transactional(readOnly = true)
    public PetInventory getPetStatus(UUID petId) {
        return petInventoryRepository.findByPetId(petId)
                .orElseThrow(() -> new PetInventoryNotFoundException(petId));
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
            eventPublisher.publish(
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
        eventPublisher.publish(
                INVENTORY_STATUS_TOPIC,
                petId.toString(),
                statusEvent,
                "InventoryStatusChangedEvent",
                petId,
                orderId,
                correlationId
        );
    }
}
