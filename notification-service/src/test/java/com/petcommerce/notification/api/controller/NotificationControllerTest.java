package com.petcommerce.notification.api.controller;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.platform.security.TestJwtIssuer;
import com.petcommerce.notification.application.NotificationService;
import com.petcommerce.notification.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService service;

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/notifications/order/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/notifications/order/10"));
        verify(service, never()).getForOrder(any(), any());
    }

    @Test
    void forgedTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/notifications/order/10").header("Authorization", bearer(TestJwtIssuer.tokenFromUntrustedKey(7))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callerFromTokenIsPassedToTheOwnershipCheck() throws Exception {
        mvc.perform(get("/api/v1/notifications/order/10").header("Authorization", bearer(TestJwtIssuer.userToken(7))))
                .andExpect(status().isOk());
        verify(service).getForOrder(eq(10L), eq(new AuthenticatedCaller(7L, "user7@example.com", false)));
    }

    @Test
    void foreignOwnerIs403WithSharedErrorBody() throws Exception {
        when(service.getForOrder(eq(10L), any())).thenThrow(new AccessDeniedException("not yours"));

        mvc.perform(get("/api/v1/notifications/order/10").header("Authorization", bearer(TestJwtIssuer.userToken(7))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void ownListUsesTheTokenUserId() throws Exception {
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", bearer(TestJwtIssuer.userToken(7))))
                .andExpect(status().isOk());
        verify(service).getForCustomer(eq(7L), eq(new AuthenticatedCaller(7L, "user7@example.com", false)));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
