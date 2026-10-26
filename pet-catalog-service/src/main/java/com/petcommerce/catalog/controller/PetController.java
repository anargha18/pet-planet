package com.petcommerce.catalog.controller;

import com.petcommerce.catalog.dto.CreatePetRequest;
import com.petcommerce.catalog.dto.PetResponse;
import com.petcommerce.catalog.dto.UpdatePetRequest;
import com.petcommerce.catalog.entity.AvailabilityStatus;
import com.petcommerce.catalog.entity.Species;
import com.petcommerce.catalog.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pets", description = "Pet catalog operations")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a pet to the catalog")
    public PetResponse create(@Valid @RequestBody CreatePetRequest request) {
        return petService.create(request);
    }

    @GetMapping("/{petId}")
    @Operation(summary = "Get a pet by ID")
    public PetResponse getById(@PathVariable UUID petId) {
        return petService.getById(petId);
    }

    @GetMapping
    @Operation(summary = "Search and page through pets")
    public Page<PetResponse> search(
            @RequestParam(required = false) Species species,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) AvailabilityStatus availabilityStatus,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return petService.search(species, breed, minPrice, maxPrice, availabilityStatus, pageable);
    }

    @PutMapping("/{petId}")
    @Operation(summary = "Update a pet")
    public PetResponse update(@PathVariable UUID petId, @Valid @RequestBody UpdatePetRequest request) {
        return petService.update(petId, request);
    }

    @DeleteMapping("/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a pet")
    public void delete(@PathVariable UUID petId) {
        petService.delete(petId);
    }
}
