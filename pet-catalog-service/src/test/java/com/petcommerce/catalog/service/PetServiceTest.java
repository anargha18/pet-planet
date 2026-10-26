package com.petcommerce.catalog.service;

import com.petcommerce.catalog.dto.CreatePetRequest;
import com.petcommerce.catalog.dto.PetResponse;
import com.petcommerce.catalog.entity.AvailabilityStatus;
import com.petcommerce.catalog.entity.Gender;
import com.petcommerce.catalog.entity.Pet;
import com.petcommerce.catalog.entity.Species;
import com.petcommerce.catalog.repository.PetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private PetService petService;

    @Test
    void createStartsPetAsAvailable() {
        CreatePetRequest request = new CreatePetRequest(
                "Milo", Species.CAT, "Maine Coon", 2, Gender.MALE,
                new BigDecimal("499.99"), "Friendly indoor cat");
        when(petRepository.save(any(Pet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetResponse response = petService.create(request);

        ArgumentCaptor<Pet> petCaptor = ArgumentCaptor.forClass(Pet.class);
        verify(petRepository).save(petCaptor.capture());
        assertThat(petCaptor.getValue().getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(response.name()).isEqualTo("Milo");
        assertThat(response.price()).isEqualByComparingTo("499.99");
    }
}
