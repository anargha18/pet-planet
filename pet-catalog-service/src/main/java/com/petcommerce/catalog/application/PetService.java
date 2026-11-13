package com.petcommerce.catalog.application;

import com.petcommerce.catalog.api.dto.CreatePetRequest;
import com.petcommerce.catalog.api.dto.PetResponse;
import com.petcommerce.catalog.api.dto.UpdatePetRequest;
import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.domain.model.Species;
import com.petcommerce.catalog.messaging.event.PetCatalogEvent;
import com.petcommerce.catalog.domain.exception.PetNotFoundException;
import com.petcommerce.catalog.persistence.repository.PetRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class PetService {

    private static final Logger log = LoggerFactory.getLogger(PetService.class);

    private final PetRepository petRepository;
    private KafkaTemplate<String, Object> kafkaTemplate;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @Autowired(required = false)
    public void setKafkaTemplate(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public PetResponse create(CreatePetRequest request) {
        Pet pet = new Pet();
        apply(pet, request);
        pet.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        Pet savedPet = petRepository.save(pet);
        log.info("Created pet id={}, species={}, availabilityStatus={}",
                savedPet.getId(), savedPet.getSpecies(), savedPet.getAvailabilityStatus());
        if (kafkaTemplate != null && savedPet.getId() != null) {
            kafkaTemplate.send("pet-catalog-events", savedPet.getId().toString(), new PetCatalogEvent(
                    UUID.randomUUID(),
                    "PetCreatedEvent",
                    1,
                    savedPet.getId(),
                    savedPet.getAvailabilityStatus().name(),
                    Instant.now()
            ));
        }
        return toResponse(savedPet);
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
        return toResponse(findPet(petId));
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
        Specification<Pet> specification = (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (species != null) {
                predicates.add(criteriaBuilder.equal(root.get("species"), species));
            }
            if (breed != null && !breed.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("breed")), "%" + breed.toLowerCase() + "%"));
            }
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (availabilityStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("availabilityStatus"), availabilityStatus));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        Page<PetResponse> results = petRepository.findAll(specification, pageable).map(this::toResponse);
        log.debug("Pet search returned {} results out of {}", results.getNumberOfElements(), results.getTotalElements());
        return results;
    }

    @Transactional
    public PetResponse update(UUID petId, UpdatePetRequest request) {
        Pet pet = findPet(petId);
        apply(pet, request);
        pet.setAvailabilityStatus(request.availabilityStatus());
        Pet updatedPet = petRepository.save(pet);
        log.info("Updated pet id={}, availabilityStatus={}", petId, updatedPet.getAvailabilityStatus());
        return toResponse(updatedPet);
    }

    @Transactional
    public void delete(UUID petId) {
        Pet pet = findPet(petId);
        petRepository.delete(pet);
        log.info("Deleted pet id={}", petId);
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

    private PetResponse toResponse(Pet pet) {
        return new PetResponse(
                pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getAge(),
                pet.getGender(), pet.getPrice(), pet.getDescription(), pet.getAvailabilityStatus(),
                pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
