package com.petcommerce.inventory.security;

import com.petcommerce.platform.security.PlatformHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Validates the caller's JWT with the shared platform setup (R-SEC-02) and applies this service's URL rules.
 * Inventory is read-only and admin-only.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformHttpSecurity platform) throws Exception {
        platform.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/inventory/**").hasRole("ADMIN")
                        .anyRequest().denyAll());
        return http.build();
    }
}
