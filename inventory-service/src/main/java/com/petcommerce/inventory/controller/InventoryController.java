package com.petcommerce.inventory.controller;

import com.petcommerce.inventory.entity.PetInventory;
import com.petcommerce.inventory.repository.PetInventoryRepository;
import com.petcommerce.inventory.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final PetInventoryRepository petInventoryRepository;
    private final InventoryService inventoryService;

    public InventoryController(PetInventoryRepository petInventoryRepository, InventoryService inventoryService) {
        this.petInventoryRepository = petInventoryRepository;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/pets/{petId}")
    public InventoryResponse getPetStatus(@PathVariable UUID petId) {
        PetInventory inventory = petInventoryRepository.findByPetId(petId)
                .orElseGet(() -> new PetInventory(petId));
        return InventoryResponse.from(inventory);
    }

    @PostMapping("/pets/{petId}/reserve")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse reservePet(@PathVariable UUID petId) {
        return InventoryResponse.from(inventoryService.reservePet(petId, UUID.randomUUID()));
    }

    @PostMapping("/pets/{petId}/release")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse releaseReservation(@PathVariable UUID petId) {
        return InventoryResponse.from(inventoryService.releaseReservation(petId));
    }

    @PostMapping("/pets/{petId}/sold")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse markSold(@PathVariable UUID petId) {
        return InventoryResponse.from(inventoryService.markSold(petId));
    }

    public record InventoryResponse(
            UUID petId,
            String status,
            UUID reservationId,
            java.time.Instant reservedAt,
            java.time.Instant reservationExpiresAt,
            java.time.Instant soldAt,
            long version
    ) {
        static InventoryResponse from(PetInventory inventory) {
            return new InventoryResponse(
                    inventory.getPetId(),
                    inventory.getStatus().name(),
                    inventory.getReservationId(),
                    inventory.getReservedAt(),
                    inventory.getReservationExpiresAt(),
                    inventory.getSoldAt(),
                    inventory.getVersion()
            );
        }
    }
}
