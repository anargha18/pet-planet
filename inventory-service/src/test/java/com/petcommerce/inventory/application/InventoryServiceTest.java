package com.petcommerce.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.domain.model.InventoryStatus;
import com.petcommerce.inventory.domain.model.PetInventory;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import com.petcommerce.inventory.messaging.event.OrderFailedEvent;
import com.petcommerce.inventory.messaging.event.PaymentSucceededEvent;
import com.petcommerce.inventory.messaging.event.PetCatalogEvent;
import com.petcommerce.inventory.messaging.publisher.InventoryEventPublisher;
import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import com.petcommerce.inventory.persistence.repository.InventoryOutboxRepository;
import com.petcommerce.inventory.persistence.repository.PetInventoryRepository;
import com.petcommerce.inventory.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final long ORDER_ID = 42L;

    @Mock
    private PetInventoryRepository petInventoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InventoryOutboxRepository inventoryOutboxRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        // Default: every event is seen for the first time; duplicate-handling tests override this.
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(petInventoryRepository.save(any(PetInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        inventoryService = new InventoryService(
                petInventoryRepository,
                processedEventRepository,
                new InventoryEventPublisher(inventoryOutboxRepository, new ObjectMapper().findAndRegisterModules()),
                Duration.ofMinutes(5)
        );
    }

    private PetInventory pet(UUID petId, InventoryStatus status, Long orderId) {
        PetInventory inventory = new PetInventory(petId);
        inventory.setStatus(status);
        inventory.setOrderId(orderId);
        ReflectionTestUtils.setField(inventory, "id", UUID.randomUUID());
        return inventory;
    }

    private OrderCreatedEvent orderCreated(UUID eventId, List<UUID> petIds) {
        return new OrderCreatedEvent(eventId, "OrderCreatedEvent", 1, UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                ORDER_ID, 101L, petIds, BigDecimal.TEN);
    }

    /** Event types written to the outbox, in order. */
    private List<String> publishedEventTypes() {
        ArgumentCaptor<InventoryOutboxEvent> captor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(inventoryOutboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(InventoryOutboxEvent::getEventType).toList();
    }

    @Test
    void orderCreatedReservesEveryAvailablePet() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PetInventory a = pet(first, InventoryStatus.AVAILABLE, null);
        PetInventory b = pet(second, InventoryStatus.AVAILABLE, null);
        when(petInventoryRepository.findByPetId(first)).thenReturn(Optional.of(a));
        when(petInventoryRepository.findByPetId(second)).thenReturn(Optional.of(b));

        inventoryService.handleOrderCreatedEvent(orderCreated(UUID.randomUUID(), List.of(first, second)));

        for (PetInventory inventory : List.of(a, b)) {
            assertEquals(InventoryStatus.RESERVED, inventory.getStatus());
            assertEquals(ORDER_ID, inventory.getOrderId());
            assertNotNull(inventory.getReservationExpiresAt());
        }
        assertEquals(a.getReservationId(), b.getReservationId(), "one reservation per order");
        assertTrue(publishedEventTypes().contains("PetReservedEvent"));
    }

    @Test
    void duplicateOrderCreatedEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.markProcessed(eventId)).thenReturn(0);

        inventoryService.handleOrderCreatedEvent(orderCreated(eventId, List.of(UUID.randomUUID())));

        verify(petInventoryRepository, never()).save(any());
        verify(inventoryOutboxRepository, never()).save(any());
    }

    @Test
    void unknownPetFailsWithPetNotFoundInsteadOfBeingCreated() {
        UUID unknown = UUID.randomUUID();
        when(petInventoryRepository.findByPetId(unknown)).thenReturn(Optional.empty());

        inventoryService.handleOrderCreatedEvent(orderCreated(UUID.randomUUID(), List.of(unknown)));

        verify(petInventoryRepository, never()).save(any());
        ArgumentCaptor<InventoryOutboxEvent> captor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(inventoryOutboxRepository).save(captor.capture());
        assertEquals("PetReservationFailedEvent", captor.getValue().getEventType());
        assertTrue(captor.getValue().getPayload().contains("PET_NOT_FOUND"));
    }

    @Test
    void reservationIsAllOrNothingWhenOnePetIsTaken() {
        UUID free = UUID.randomUUID();
        UUID taken = UUID.randomUUID();
        PetInventory freePet = pet(free, InventoryStatus.AVAILABLE, null);
        when(petInventoryRepository.findByPetId(free)).thenReturn(Optional.of(freePet));
        when(petInventoryRepository.findByPetId(taken)).thenReturn(Optional.of(pet(taken, InventoryStatus.RESERVED, 99L)));

        inventoryService.handleOrderCreatedEvent(orderCreated(UUID.randomUUID(), List.of(free, taken)));

        assertEquals(InventoryStatus.AVAILABLE, freePet.getStatus());
        assertEquals(List.of("PetReservationFailedEvent"), publishedEventTypes());
    }

    @Test
    void paymentSucceededSellsPetsStillReservedForTheOrder() {
        PetInventory reserved = pet(UUID.randomUUID(), InventoryStatus.RESERVED, ORDER_ID);
        when(petInventoryRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(reserved));

        inventoryService.handlePaymentSucceededEvent(paymentSucceeded());

        assertEquals(InventoryStatus.SOLD, reserved.getStatus());
        assertNotNull(reserved.getSoldAt());
        assertTrue(publishedEventTypes().contains("PetsSoldEvent"));
    }

    @Test
    void paymentSucceededAfterReservationWasLostReportsSaleConflictAndSellsNothing() {
        when(petInventoryRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        inventoryService.handlePaymentSucceededEvent(paymentSucceeded());

        verify(petInventoryRepository, never()).save(any());
        assertEquals(List.of("InventorySaleConflictEvent"), publishedEventTypes());
    }

    @Test
    void orderFailedReleasesOnlyPetsStillReservedForTheOrder() {
        PetInventory reserved = pet(UUID.randomUUID(), InventoryStatus.RESERVED, ORDER_ID);
        PetInventory alreadySold = pet(UUID.randomUUID(), InventoryStatus.SOLD, ORDER_ID);
        when(petInventoryRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(reserved, alreadySold));

        inventoryService.handleOrderFailedEvent(new OrderFailedEvent(UUID.randomUUID(), "OrderFailedEvent", 1,
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), ORDER_ID, 101L, List.of(), "PAYMENT_FAILED:PAYMENT_DECLINED"));

        assertEquals(InventoryStatus.AVAILABLE, reserved.getStatus());
        assertNull(reserved.getOrderId());
        assertEquals(InventoryStatus.SOLD, alreadySold.getStatus());
    }

    @Test
    void archivedAvailablePetIsRetiredButReservedPetIsNot() {
        UUID availableId = UUID.randomUUID();
        UUID reservedId = UUID.randomUUID();
        PetInventory available = pet(availableId, InventoryStatus.AVAILABLE, null);
        PetInventory reserved = pet(reservedId, InventoryStatus.RESERVED, ORDER_ID);
        when(petInventoryRepository.findByPetId(availableId)).thenReturn(Optional.of(available));
        when(petInventoryRepository.findByPetId(reservedId)).thenReturn(Optional.of(reserved));

        inventoryService.handlePetCatalogEvent(archived(availableId));
        inventoryService.handlePetCatalogEvent(archived(reservedId));

        assertEquals(InventoryStatus.RETIRED, available.getStatus());
        assertEquals(InventoryStatus.RESERVED, reserved.getStatus());
    }

    private PaymentSucceededEvent paymentSucceeded() {
        return new PaymentSucceededEvent(UUID.randomUUID(), "PaymentSucceededEvent", 1, UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), BigDecimal.TEN);
    }

    private PetCatalogEvent archived(UUID petId) {
        return new PetCatalogEvent(UUID.randomUUID(), "PetArchivedEvent", 1, petId, "RETIRED", Instant.now());
    }
}
