package com.petcommerce.order.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.api.dto.CreateOrderRequest;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.domain.model.Species;
import com.petcommerce.order.domain.exception.InvalidOrderStateException;
import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.exception.PetCatalogUnavailableException;
import com.petcommerce.order.application.OrderCommandService;
import com.petcommerce.order.application.OrderQueryService;
import com.petcommerce.platform.security.TestJwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(com.petcommerce.order.security.SecurityConfig.class)
@EnableMethodSecurity
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderCommandService orderCommandService;

    @MockBean
    private OrderQueryService orderQueryService;

    @MockBean
    private PetCatalogClient petCatalogClient;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Test
    void createOrderReturnsCreated() throws Exception {
        UUID petId = UUID.randomUUID();
        var orderResponse = new com.petcommerce.order.api.dto.OrderResponse(
                1L,
                101L,
                OrderStatus.CREATED,
                BigDecimal.valueOf(75000),
                List.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        Mockito.when(orderCommandService.create(any(CreateOrderRequest.class), any())).thenReturn(orderResponse);

        String body = "{\"items\":[{\"petId\":\"" + petId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .with(user("7").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void getOrderReturnsOk() throws Exception {
        var orderResponse = new com.petcommerce.order.api.dto.OrderResponse(
                1L,
                101L,
                OrderStatus.CREATED,
                BigDecimal.valueOf(75000),
                List.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        Mockito.when(orderQueryService.getOrder(eq(1L), any())).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(user("7").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        String body = "{\"items\":[]}";

        mockMvc.perform(post("/api/v1/orders")
                        .with(user("7").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundReturns404() throws Exception {
        Mockito.when(orderQueryService.getOrder(eq(999L), any())).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/v1/orders/999")
                        .with(user("7").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelConflictReturns409() throws Exception {
        Mockito.when(orderCommandService.cancel(eq(1L), any())).thenThrow(new InvalidOrderStateException(1L, "PAID"));

        mockMvc.perform(patch("/api/v1/orders/1/cancel")
                        .with(user("7").roles("USER")))
                .andExpect(status().isConflict());
    }

    @Test
    void petCatalogUnavailableReturns503() throws Exception {
        Mockito.when(orderCommandService.create(any(CreateOrderRequest.class), any()))
                .thenThrow(new PetCatalogUnavailableException("Pet Catalog service is unavailable"));

        String body = "{\"items\":[{\"petId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .with(user("7").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void malformedJsonBodyReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(user("7").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"petId\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutTokenIs401WithSharedErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/1"));
    }

    @Test
    void realSignedTokenIsAcceptedAndCallerIsTheTokenSubject() throws Exception {
        var orderResponse = new com.petcommerce.order.api.dto.OrderResponse(
                1L, 7L, OrderStatus.CREATED, BigDecimal.ONE, List.of(), java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(orderQueryService.getOrder(eq(1L), Mockito.argThat(auth -> "7".equals(auth.getName()))))
                .thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/1")
                        .header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(7));
    }

    @Test
    void notFoundUsesSharedErrorBody() throws Exception {
        Mockito.when(orderQueryService.getOrder(eq(404L), any())).thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(get("/api/v1/orders/404").with(user("7").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
