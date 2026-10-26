package com.petcommerce.inventory.repository;

import com.petcommerce.inventory.entity.InventoryStatus;
import com.petcommerce.inventory.entity.PetInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PetInventoryRepository extends JpaRepository<PetInventory, UUID> {

    Optional<PetInventory> findByPetId(UUID petId);

    List<PetInventory> findByStatusAndReservationExpiresAtBefore(InventoryStatus status, Instant before);
}
