package com.petcommerce.catalog.persistence.repository;

import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.domain.model.Species;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;

/** Query building blocks for {@link PetRepository}. */
public final class PetSpecifications {

    private PetSpecifications() {
    }

    /** Catalog search: every argument is an optional filter (null/blank = not applied). */
    public static Specification<Pet> search(
            Species species,
            String breed,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AvailabilityStatus availabilityStatus
    ) {
        return (root, query, criteriaBuilder) -> {
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
    }
}
