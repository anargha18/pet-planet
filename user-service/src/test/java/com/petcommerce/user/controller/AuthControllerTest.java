package com.petcommerce.user.controller;

import com.petcommerce.user.config.SecurityConfig;
import com.petcommerce.user.entity.Role;
import com.petcommerce.user.entity.User;
import com.petcommerce.user.security.JwtService;
import com.petcommerce.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableMethodSecurity
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    void customerCannotAccessAdminUserRoute() throws Exception {
        UUID userId = UUID.randomUUID();
        User customer = new User("customer@example.com", "encodedPassword", "Customer", "User", Role.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", userId);
        when(userService.findById(userId)).thenReturn(customer);

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessUserRoute() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = new User("admin@example.com", "encodedPassword", "Admin", "User", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", userId);

        when(userService.findById(userId)).thenReturn(admin);

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
