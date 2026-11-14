package com.petcommerce.catalog.application;

import com.petcommerce.catalog.api.dto.CreatePetRequest;
import com.petcommerce.catalog.api.dto.PetResponse;
import com.petcommerce.catalog.api.dto.UpdatePetRequest;
import com.petcommerce.catalog.domain.exception.PetNotArchivableException;
import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Gender;
import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.domain.model.Species;
import com.petcommerce.catalog.messaging.publisher.PetCatalogEventPublisher;
import com.petcommerce.catalog.persistence.repository.PetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetCatalogEventPublisher petCatalogEventPublisher;

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

    @Test
    void archivingAnAvailablePetRetiresItAndPublishesArchivedEvent() {
        Pet pet = existingPet(AvailabilityStatus.AVAILABLE);

        petService.archive(pet.getId());

        assertThat(pet.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RETIRED);
        verify(petCatalogEventPublisher).publishPetArchived(pet);
    }

    @Test
    void archivingAReservedPetIsRejected() {
        Pet pet = existingPet(AvailabilityStatus.RESERVED);

        assertThatThrownBy(() -> petService.archive(pet.getId())).isInstanceOf(PetNotArchivableException.class);

        assertThat(pet.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        verify(petCatalogEventPublisher, never()).publishPetArchived(any());
    }

    @Test
    void updateCannotChangeAvailabilityOwnedByInventory() {
        Pet pet = existingPet(AvailabilityStatus.SOLD);
        when(petRepository.save(any(Pet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetResponse response = petService.update(pet.getId(), new UpdatePetRequest(
                "Milo", Species.CAT, "Maine Coon", 3, Gender.MALE, new BigDecimal("450.00"), "Updated"));

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.SOLD);
        assertThat(response.price()).isEqualByComparingTo("450.00");
    }

    private Pet existingPet(AvailabilityStatus status) {
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID());
        pet.setName("Milo");
        pet.setSpecies(Species.CAT);
        pet.setBreed("Maine Coon");
        pet.setGender(Gender.MALE);
        pet.setPrice(new BigDecimal("499.99"));
        pet.setDescription("Friendly");
        pet.setAvailabilityStatus(status);
        when(petRepository.findById(pet.getId())).thenReturn(Optional.of(pet));
        return pet;
    }
}
