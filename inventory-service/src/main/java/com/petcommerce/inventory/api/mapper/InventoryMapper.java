package com.petcommerce.inventory.api.mapper;

import com.petcommerce.inventory.api.dto.InventoryResponse;
import com.petcommerce.inventory.domain.model.PetInventory;

/** Converts inventory entities to API response records. */
public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(PetInventory inventory) {
        return new InventoryResponse(
                inventory.getPetId(),
                inventory.getStatus().name(),
                inventory.getOrderId(),
                inventory.getReservationId(),
                inventory.getReservedAt(),
                inventory.getReservationExpiresAt(),
                inventory.getSoldAt(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}
