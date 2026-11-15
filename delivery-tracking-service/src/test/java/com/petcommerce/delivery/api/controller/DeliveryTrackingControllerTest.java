package com.petcommerce.delivery.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.platform.security.TestJwtIssuer;
import com.petcommerce.delivery.application.DeliveryTrackingService;
import com.petcommerce.delivery.domain.exception.DeliveryNotFoundException;
import com.petcommerce.delivery.domain.exception.InvalidDeliveryTransitionException;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP security of the read API with real RS256 tokens (R-SEC-02) and the shared error body (R-SEC-06). */
@WebMvcTest(DeliveryTrackingController.class)
@Import(SecurityConfig.class)
class DeliveryTrackingControllerTest {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DeliveryTrackingService service;

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/deliveries/order/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/deliveries/order/10"));
        verify(service, never()).getForOrder(any(), any());
    }

    @Test
    void forgedTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/deliveries/order/10").header("Authorization", bearer(TestJwtIssuer.tokenFromUntrustedKey(7))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callerFromTokenIsPassedToTheOwnershipCheck() throws Exception {
        mvc.perform(get("/api/v1/deliveries/order/10").header("Authorization", bearer(TestJwtIssuer.userToken(7))))
                .andExpect(status().isOk());
        verify(service).getForOrder(eq(10L), eq(new AuthenticatedCaller(7L, "user7@example.com", false)));
    }

    @Test
    void foreignOwnerIs403WithSharedErrorBody() throws Exception {
        when(service.getForOrder(eq(10L), any())).thenThrow(new AccessDeniedException("not yours"));

        mvc.perform(get("/api/v1/deliveries/order/10").header("Authorization", bearer(TestJwtIssuer.userToken(7))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void missingRecordIs404WithSharedErrorBody() throws Exception {
        when(service.getForOrder(eq(10L), any())).thenThrow(new DeliveryNotFoundException(10L));

        mvc.perform(get("/api/v1/deliveries/order/10").header("Authorization", bearer(TestJwtIssuer.adminToken(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("found for order 10")));
    }

    @Test
    void advancingDeliveryIsAdminOnly() throws Exception {
        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.userToken(7)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(service, never()).advance(any(), any(), any());
    }

    @Test
    void adminAdvancesDelivery() throws Exception {
        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.adminToken(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_FOR_DELIVERY\",\"note\":\"Van 3\"}"))
                .andExpect(status().isOk());
        verify(service).advance(10L, DeliveryStatus.OUT_FOR_DELIVERY, "Van 3");
    }

    @Test
    void unknownOrderIs404() throws Exception {
        when(service.advance(eq(10L), any(), any())).thenThrow(new DeliveryNotFoundException(10L));

        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.adminToken(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void illegalTransitionIs409() throws Exception {
        when(service.advance(eq(10L), any(), any())).thenThrow(new InvalidDeliveryTransitionException(
                10L, DeliveryStatus.DELIVERED, DeliveryStatus.OUT_FOR_DELIVERY));

        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.adminToken(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingOrUnknownStatusIs400() throws Exception {
        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.adminToken(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/deliveries/order/10/status").header("Authorization", bearer(TestJwtIssuer.adminToken(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TELEPORTED\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).advance(any(), any(), any());
    }

    @Test
    void oldDeliveredEndpointIsGone() throws Exception {
        mvc.perform(put("/api/v1/deliveries/order/10/delivered").header("Authorization", bearer(TestJwtIssuer.adminToken(1))))
                .andExpect(status().isForbidden());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
