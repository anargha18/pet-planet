package com.petcommerce.order.security;

import com.petcommerce.platform.security.PlatformHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Validates the caller's JWT with the shared platform setup (R-SEC-02) and applies this service's URL rules.
 * Any signed-in user; ownership is checked per order in {@code OrderAccessPolicy}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformHttpSecurity platform) throws Exception {
        platform.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().denyAll());
        return http.build();
    }
}
