package com.petcommerce.user.api.controller;

import com.petcommerce.platform.security.TestJwtIssuer;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.application.UserService;
import com.petcommerce.user.security.InternalServiceKeyFilter;
import com.petcommerce.user.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InternalServiceKeyFilter.class})
@TestPropertySource(properties = "internal.service-token=test-internal-token")
class UserControllerTest {

    private static final String INTERNAL = "X-Internal-Service-Token";

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void profileUsesUserIdFromVerifiedToken() throws Exception {
        when(userService.findActiveById(7)).thenReturn(new UserResponse(
                7, "user@example.com", "Test", "User", "USER", true, Instant.EPOCH, Instant.EPOCH
        ));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(INTERNAL, "test-internal-token")
                        .header("Authorization", "Bearer " + TestJwtIssuer.userToken(7))
                        .header("X-Authenticated-User-Id", "999")) // ignored: identity comes only from the token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).findActiveById(7);
    }

    @Test
    void profileWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(INTERNAL, "test-internal-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(userService, never()).findActiveById(any());
    }

    @Test
    void userAdministrationNeedsAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/users/5")
                        .header(INTERNAL, "test-internal-token")
                        .header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rejectsRequestsWithoutInternalServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Internal service authentication required"));
    }
}
