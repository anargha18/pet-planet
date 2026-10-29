package com.petcommerce.order.client;

import com.petcommerce.order.exception.PetCatalogUnavailableException;
import com.petcommerce.order.exception.PetNotFoundException;
import com.petcommerce.order.exception.PetUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class PetCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(PetCatalogClient.class);

    private final RestClient restClient;

    public PetCatalogClient(@Value("${pet.catalog.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public PetCatalogPetResponse getPetById(UUID petId) {
        try {
            log.info("Fetching pet {} from Pet Catalog", petId);
            ResponseEntity<PetCatalogPetResponse> response = restClient.get()
                    .uri("/api/v1/pets/{petId}", petId)
                    .retrieve()
                    .toEntity(PetCatalogPetResponse.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new PetNotFoundException(petId);
            }

            PetCatalogPetResponse pet = response.getBody();
            if (pet.availabilityStatus() == null || !"AVAILABLE".equalsIgnoreCase(pet.availabilityStatus())) {
                throw new PetUnavailableException(petId);
            }
            return pet;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PetNotFoundException(petId);
        } catch (ResourceAccessException ex) {
            throw new PetCatalogUnavailableException("Pet Catalog service is unavailable");
        }
    }
}
