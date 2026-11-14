package com.petcommerce.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shared resource-server setup end to end: real RS256 tokens → platform decoder and validators → claim mapping →
 * AuthenticatedCaller ownership check → shared ApiError bodies.
 */
@SpringBootTest(classes = PlatformSecurityTest.TestApp.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                + "com.petcommerce.platform.kafka.KafkaPlatformAutoConfiguration")
@AutoConfigureMockMvc
class PlatformSecurityTest {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void ownerReadsOwnRecord() throws Exception {
        mvc.perform(get("/things/owner/7").header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.email").value("user7@example.com"));
    }

    @Test
    void foreignOwnerIsRejectedWithSharedErrorShape() throws Exception {
        mvc.perform(get("/things/owner/8").header("Authorization", "Bearer " + TestJwtIssuer.userToken(7))
                        .header("X-Correlation-Id", "test-corr-1"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-Id", "test-corr-1"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/things/owner/8"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").value("test-corr-1"));
    }

    @Test
    void adminReadsAnyRecord() throws Exception {
        mvc.perform(get("/things/owner/8").header("Authorization", "Bearer " + TestJwtIssuer.adminToken(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void roleRuleInFilterChainUsesRoleClaim() throws Exception {
        mvc.perform(get("/admin/ping").header("Authorization", "Bearer " + TestJwtIssuer.userToken(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/admin/ping").header("Authorization", "Bearer " + TestJwtIssuer.adminToken(1)))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenIs401WithSharedErrorShape() throws Exception {
        mvc.perform(get("/things/owner/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/things/owner/7"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void invalidTokensAre401() throws Exception {
        String[] invalid = {
                TestJwtIssuer.tokenFromUntrustedKey(7),
                TestJwtIssuer.token(c -> c.subject("7").claim("role", "ROLE_USER").issuer("someone-else")),
                TestJwtIssuer.token(c -> c.subject("7").claim("role", "ROLE_USER").audience("other-app")),
                TestJwtIssuer.token(c -> c.subject("7").claim("role", "ROLE_USER")
                        .expirationTime(Date.from(Instant.now().minusSeconds(600)))),
                TestJwtIssuer.token(c -> c.subject("user@example.com").claim("role", "ROLE_USER")),
                TestJwtIssuer.token(c -> c.subject("7").claim("role", "ROLE_SUPERUSER")),
                TestJwtIssuer.token(c -> c.subject("7")),
                "not-a-jwt"
        };
        for (String token : invalid) {
            mvc.perform(get("/things/owner/7").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        SecurityFilterChain chain(HttpSecurity http, PlatformHttpSecurity platform) throws Exception {
            platform.apply(http).authorizeHttpRequests(auth -> auth
                    .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated());
            return http.build();
        }

        @Bean
        ThingController thingController() {
            return new ThingController();
        }
    }

    @RestController
    static class ThingController {

        @GetMapping("/things/owner/{customerId}")
        Map<String, Object> thing(AuthenticatedCaller caller, @PathVariable Long customerId) {
            caller.requireAccessTo(customerId);
            return Map.of("userId", caller.userId(), "admin", caller.admin(),
                    "email", caller.email() == null ? "" : caller.email());
        }

        @GetMapping("/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
