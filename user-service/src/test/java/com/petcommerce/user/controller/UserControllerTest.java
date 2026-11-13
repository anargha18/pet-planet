package com.petcommerce.user.controller;

import com.petcommerce.user.config.SecurityConfig;
import com.petcommerce.user.dto.UserResponse;
import com.petcommerce.user.security.InternalServiceKeyFilter;
import com.petcommerce.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InternalServiceKeyFilter.class})
@TestPropertySource(properties = "internal.service-token=test-internal-token")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void profileUsesGatewayAuthenticatedIntegerUserId() throws Exception {
        when(userService.findActiveById(7)).thenReturn(new UserResponse(
                7, "user@example.com", "Test", "User", "USER", true, Instant.EPOCH, Instant.EPOCH
        ));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Internal-Service-Token", "test-internal-token")
                        .header("X-Authenticated-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).findActiveById(7);
    }

    @Test
    void rejectsRequestsWithoutInternalServiceToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Authenticated-User-Id", "7"))
                .andExpect(status().isUnauthorized());
    }
}
