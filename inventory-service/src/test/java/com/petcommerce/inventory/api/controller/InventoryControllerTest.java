package com.petcommerce.inventory.api.controller;

import com.petcommerce.inventory.application.InventoryService;
import com.petcommerce.inventory.domain.model.InventoryStatus;
import com.petcommerce.inventory.domain.model.PetInventory;
import com.petcommerce.inventory.security.SecurityConfig;
import com.petcommerce.platform.security.TestJwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Read-only admin inventory overview (Phase 5, for the admin UI). */
@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerTest {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InventoryService service;

    @Test
    void adminListsInventoryFilteredByStatus() throws Exception {
        PetInventory pet = new PetInventory(UUID.randomUUID());
        when(service.listInventory(eq(InventoryStatus.AVAILABLE), any())).thenReturn(new PageImpl<>(List.of(pet)));

        mvc.perform(get("/api/v1/inventory/pets?status=AVAILABLE").header("Authorization", "Bearer " + TestJwtIssuer.adminToken(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].petId").value(pet.getPetId().toString()));
    }

    @Test
    void userIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/inventory/pets").header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isForbidden());
        verify(service, never()).listInventory(any(), any());
    }
}
