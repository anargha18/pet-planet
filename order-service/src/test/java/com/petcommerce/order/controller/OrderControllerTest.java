package com.petcommerce.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.client.PetCatalogClient;
import com.petcommerce.order.client.PetCatalogPetResponse;
import com.petcommerce.order.dto.CreateOrderRequest;
import com.petcommerce.order.entity.Order;
import com.petcommerce.order.entity.OrderStatus;
import com.petcommerce.order.entity.Species;
import com.petcommerce.order.exception.InvalidOrderStateException;
import com.petcommerce.order.exception.OrderNotFoundException;
import com.petcommerce.order.exception.PetCatalogUnavailableException;
import com.petcommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private PetCatalogClient petCatalogClient;

    @Test
    void createOrderReturnsCreated() throws Exception {
        UUID petId = UUID.randomUUID();
        var orderResponse = new com.petcommerce.order.dto.OrderResponse(
                1L,
                101L,
                OrderStatus.CREATED,
                BigDecimal.valueOf(75000),
                List.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        Mockito.when(orderService.create(any(CreateOrderRequest.class))).thenReturn(orderResponse);

        String body = "{\"customerId\":101,\"items\":[{\"petId\":\"" + petId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void getOrderReturnsOk() throws Exception {
        var orderResponse = new com.petcommerce.order.dto.OrderResponse(
                1L,
                101L,
                OrderStatus.CREATED,
                BigDecimal.valueOf(75000),
                List.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        Mockito.when(orderService.getOrder(1L)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        String body = "{\"customerId\":null,\"items\":[]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundReturns404() throws Exception {
        Mockito.when(orderService.getOrder(999L)).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelConflictReturns409() throws Exception {
        Mockito.when(orderService.cancel(1L)).thenThrow(new InvalidOrderStateException(1L, "PAID"));

        mockMvc.perform(patch("/api/v1/orders/1/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void petCatalogUnavailableReturns503() throws Exception {
        Mockito.when(orderService.create(any(CreateOrderRequest.class)))
                .thenThrow(new PetCatalogUnavailableException("Pet Catalog service is unavailable"));

        String body = "{\"customerId\":101,\"items\":[{\"petId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable());
    }
}
