package com.petcommerce.inventory.api.controller;

import com.petcommerce.inventory.api.dto.InventoryResponse;
import com.petcommerce.inventory.api.mapper.InventoryMapper;
import com.petcommerce.inventory.application.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only inventory view for admins (R-SAGA-07). Reservations, releases and sales happen only through
 * the checkout saga, so every change has an order and a compensating path.
 */
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
}
