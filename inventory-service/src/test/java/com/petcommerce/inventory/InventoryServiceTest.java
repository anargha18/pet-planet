package com.petcommerce.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.entity.InventoryStatus;
import com.petcommerce.inventory.entity.PetInventory;
import com.petcommerce.inventory.event.OrderConfirmedEvent;
import com.petcommerce.inventory.event.OrderCreatedEvent;
import com.petcommerce.inventory.repository.InventoryOutboxRepository;
import com.petcommerce.inventory.repository.PetInventoryRepository;
import com.petcommerce.inventory.repository.ProcessedEventRepository;
import com.petcommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private PetInventoryRepository petInventoryRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private Environment environment;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InventoryOutboxRepository inventoryOutboxRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("inventory.reservation-expiration-minutes", "5")).thenReturn("5");
        inventoryService = new InventoryService(
                petInventoryRepository,
                processedEventRepository,
                inventoryOutboxRepository,
                kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                environment
        );
    }

    @Test
    void reservePetSuccessfully() {
        UUID petId = UUID.randomUUID();
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.empty());
        when(petInventoryRepository.save(any(PetInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetInventory inventory = inventoryService.reservePet(petId, UUID.randomUUID());

        assertEquals(InventoryStatus.RESERVED, inventory.getStatus());
        assertNotNull(inventory.getReservationId());
        assertNotNull(inventory.getReservedAt());
    }

    @Test
    void rejectReservationWhenPetAlreadyReserved() {
        UUID petId = UUID.randomUUID();
        PetInventory inventory = new PetInventory(petId);
        inventory.setStatus(InventoryStatus.RESERVED);
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.of(inventory));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inventoryService.reservePet(petId, UUID.randomUUID()));

        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void handleOrderCreatedEventReservesAvailablePets() {
        UUID petId = UUID.randomUUID();
        PetInventory inventory = new PetInventory(petId);
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.of(inventory));
        when(petInventoryRepository.save(any(PetInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                "OrderCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                42L,
                List.of(petId)
        );

        assertDoesNotThrow(() -> inventoryService.handleOrderCreatedEvent(event));
        assertEquals(InventoryStatus.RESERVED, inventory.getStatus());
        verify(petInventoryRepository, atLeastOnce()).save(any(PetInventory.class));
    }

    @Test
    void duplicateOrderCreatedEventIsIgnoredSafely() {
        UUID petId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PetInventory inventory = new PetInventory(petId);
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.of(inventory));
        when(processedEventRepository.existsById(eventId)).thenReturn(false, true);
        when(petInventoryRepository.save(any(PetInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                "OrderCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                99L,
                List.of(petId)
        );

        inventoryService.handleOrderCreatedEvent(event);
        inventoryService.handleOrderCreatedEvent(event);

        assertEquals(InventoryStatus.RESERVED, inventory.getStatus());
        verify(petInventoryRepository, times(1)).save(any(PetInventory.class));
    }

    @Test
    void reservePetFailsWhenOptimisticLockConflictOccurs() {
        UUID petId = UUID.randomUUID();
        PetInventory inventory = new PetInventory(petId);
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.of(inventory));
        when(petInventoryRepository.save(any(PetInventory.class)))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inventoryService.reservePet(petId, UUID.randomUUID()));

        assertTrue(ex.getMessage().contains("already reserved"));
    }

    @Test
    void handleOrderConfirmedEventMarksPetsSold() {
        UUID petId = UUID.randomUUID();
        PetInventory inventory = new PetInventory(petId);
        inventory.setStatus(InventoryStatus.RESERVED);
        when(processedEventRepository.existsById(any(UUID.class))).thenReturn(false);
        when(petInventoryRepository.findByPetId(petId)).thenReturn(Optional.of(inventory));
        when(petInventoryRepository.save(any(PetInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                UUID.randomUUID(),
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                77L,
                List.of(petId),
                BigDecimal.valueOf(50000)
        );

        assertDoesNotThrow(() -> inventoryService.handleOrderConfirmedEvent(event));
        assertEquals(InventoryStatus.SOLD, inventory.getStatus());
    }
}
