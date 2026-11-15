package com.petcommerce.order.client;

import com.petcommerce.order.domain.exception.PetCatalogUnavailableException;
import com.petcommerce.order.domain.exception.PetNotFoundException;
import com.petcommerce.order.domain.exception.PetUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

/**
 * Reads a pet's price snapshot from pet-catalog-service (R-SVC-04). Connect/read timeouts are bounded, and catalog
 * answers are mapped: 404 → {@link PetNotFoundException}, not AVAILABLE → {@link PetUnavailableException}, any other
 * error status, timeout or connection failure → {@link PetCatalogUnavailableException} (503).
 */
@Component
public class PetCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(PetCatalogClient.class);

    private final RestClient restClient;

    public PetCatalogClient(@Value("${pet.catalog.base-url}") String baseUrl,
                            @Value("${pet.catalog.connect-timeout:PT2S}") Duration connectTimeout,
                            @Value("${pet.catalog.read-timeout:PT5S}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public PetCatalogPetResponse getPetById(UUID petId) {
        PetCatalogPetResponse pet;
        try {
            log.info("Fetching pet {} from Pet Catalog", petId);
            pet = restClient.get()
                    .uri("/api/v1/pets/{petId}", petId)
                    .retrieve()
                    .body(PetCatalogPetResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PetNotFoundException(petId);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.warn("Pet Catalog answered {} for pet {}", ex.getStatusCode(), petId);
            throw new PetCatalogUnavailableException("Pet Catalog service returned " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("Pet Catalog unreachable or too slow for pet {}: {}", petId, ex.getMessage());
            throw new PetCatalogUnavailableException("Pet Catalog service is unavailable");
        } catch (RestClientException ex) {
            log.warn("Unreadable Pet Catalog response for pet {}: {}", petId, ex.getMessage());
            throw new PetCatalogUnavailableException("Pet Catalog service returned an unreadable response");
        }
        if (pet == null) {
            throw new PetNotFoundException(petId);
        }
        if (pet.availabilityStatus() == null || !"AVAILABLE".equalsIgnoreCase(pet.availabilityStatus())) {
            throw new PetUnavailableException(petId);
        }
        return pet;
    }
}
