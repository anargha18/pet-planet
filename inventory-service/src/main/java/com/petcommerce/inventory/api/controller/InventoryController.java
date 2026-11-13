package com.petcommerce.inventory.api.controller;

import com.petcommerce.inventory.api.dto.InventoryResponse;
import com.petcommerce.inventory.api.mapper.InventoryMapper;
import com.petcommerce.inventory.application.InventoryService;
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

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/pets/{petId}")
    public InventoryResponse getPetStatus(@PathVariable UUID petId) {
        return InventoryMapper.toResponse(inventoryService.getPetStatus(petId));
    }

    @PostMapping("/pets/{petId}/reserve")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse reservePet(@PathVariable UUID petId) {
        return InventoryMapper.toResponse(inventoryService.reservePet(petId, UUID.randomUUID()));
    }

    @PostMapping("/pets/{petId}/release")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse releaseReservation(@PathVariable UUID petId) {
        return InventoryMapper.toResponse(inventoryService.releaseReservation(petId));
    }

    @PostMapping("/pets/{petId}/sold")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse markSold(@PathVariable UUID petId) {
        return InventoryMapper.toResponse(inventoryService.markSold(petId));
    }
}
