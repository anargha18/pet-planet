package com.petcommerce.catalog.application;

import com.petcommerce.catalog.api.dto.CreatePetRequest;
import com.petcommerce.catalog.api.dto.PetResponse;
import com.petcommerce.catalog.api.dto.UpdatePetRequest;
import com.petcommerce.catalog.api.mapper.PetMapper;
import com.petcommerce.catalog.domain.exception.PetNotArchivableException;
import com.petcommerce.catalog.domain.exception.PetNotFoundException;
import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.domain.model.Species;
import com.petcommerce.catalog.messaging.publisher.PetCatalogEventPublisher;
import com.petcommerce.catalog.persistence.repository.PetRepository;
import com.petcommerce.catalog.persistence.repository.PetSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PetService {

    private static final Logger log = LoggerFactory.getLogger(PetService.class);

    private final PetRepository petRepository;
    private final PetCatalogEventPublisher petCatalogEventPublisher;

    public PetService(PetRepository petRepository, PetCatalogEventPublisher petCatalogEventPublisher) {
        this.petRepository = petRepository;
        this.petCatalogEventPublisher = petCatalogEventPublisher;
    }

    @Transactional
    public PetResponse create(CreatePetRequest request) {
        Pet pet = new Pet();
        apply(pet, request);
        pet.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        Pet savedPet = petRepository.save(pet);
        log.info("Created pet id={}, species={}, availabilityStatus={}",
                savedPet.getId(), savedPet.getSpecies(), savedPet.getAvailabilityStatus());
        petCatalogEventPublisher.publishPetCreated(savedPet);
        return PetMapper.toResponse(savedPet);
    }

    @Transactional
    public void syncAvailabilityStatus(UUID petId, String statusValue) {
        if (petId == null || statusValue == null) {
            return;
        }
        petRepository.findById(petId).ifPresent(pet -> {
            try {
                AvailabilityStatus nextStatus = AvailabilityStatus.valueOf(statusValue.toUpperCase());
                if (pet.getAvailabilityStatus() != nextStatus) {
                    pet.setAvailabilityStatus(nextStatus);
                    petRepository.save(pet);
                    log.info("Synchronized catalog pet {} availabilityStatus to {}", petId, nextStatus);
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring unknown availability status {} for pet {}", statusValue, petId);
            }
        });
    }

    @Transactional(readOnly = true)
    public PetResponse getById(UUID petId) {
        log.debug("Fetching pet id={}", petId);
        return PetMapper.toResponse(findPet(petId));
    }

    @Transactional(readOnly = true)
    public Page<PetResponse> search(
            Species species,
            String breed,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AvailabilityStatus availabilityStatus,
            Pageable pageable
    ) {
        log.debug("Searching pets species={}, breed={}, minPrice={}, maxPrice={}, availabilityStatus={}, page={}, size={}",
                species, breed, minPrice, maxPrice, availabilityStatus, pageable.getPageNumber(), pageable.getPageSize());
        Specification<Pet> specification = PetSpecifications.search(species, breed, minPrice, maxPrice, availabilityStatus);
        if (availabilityStatus == null) {
            specification = specification.and(PetSpecifications.notRetired());
        }

        Page<PetResponse> results = petRepository.findAll(specification, pageable).map(PetMapper::toResponse);
        log.debug("Pet search returned {} results out of {}", results.getNumberOfElements(), results.getTotalElements());
        return results;
    }

    @Transactional
    public PetResponse update(UUID petId, UpdatePetRequest request) {
        Pet pet = findPet(petId);
        apply(pet, request);
        // availabilityStatus is owned by inventory and not changeable here (R-SAGA-05).
        Pet updatedPet = petRepository.save(pet);
        log.info("Updated pet id={}, availabilityStatus={}", petId, updatedPet.getAvailabilityStatus());
        return PetMapper.toResponse(updatedPet);
    }

    @Transactional
    /**
     * Archives (soft-deletes) a pet: it becomes RETIRED, disappears from the default listing, and inventory retires it.
     * The row is kept so order snapshots and history stay valid. Reserved or sold pets cannot be archived.
     */
    public void archive(UUID petId) {
        Pet pet = findPet(petId);
        if (pet.getAvailabilityStatus() == AvailabilityStatus.RETIRED) {
            return;
        }
        if (pet.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new PetNotArchivableException(petId, pet.getAvailabilityStatus().name());
        }
        pet.setAvailabilityStatus(AvailabilityStatus.RETIRED);
        petRepository.save(pet);
        petCatalogEventPublisher.publishPetArchived(pet);
        log.info("Archived pet id={}", petId);
    }

    private Pet findPet(UUID petId) {
        return petRepository.findById(petId).orElseThrow(() -> {
            log.warn("Pet not found id={}", petId);
            return new PetNotFoundException(petId);
        });
    }

    private void apply(Pet pet, CreatePetRequest request) {
        pet.setName(request.name());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setAge(request.age());
        pet.setGender(request.gender());
        pet.setPrice(request.price());
        pet.setDescription(request.description());
    }

    private void apply(Pet pet, UpdatePetRequest request) {
        pet.setName(request.name());
        pet.setSpecies(request.species());
        pet.setBreed(request.breed());
        pet.setAge(request.age());
        pet.setGender(request.gender());
        pet.setPrice(request.price());
        pet.setDescription(request.description());
    }
}
