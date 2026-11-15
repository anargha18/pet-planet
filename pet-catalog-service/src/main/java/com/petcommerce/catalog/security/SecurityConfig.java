package com.petcommerce.catalog.security;

import com.petcommerce.platform.security.PlatformHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Validates the caller's JWT with the shared platform setup (R-SEC-02) and applies this service's URL rules.
 * Catalog reads are public; every change needs ROLE_ADMIN.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformHttpSecurity platform) throws Exception {
        platform.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
                        .requestMatchers("/api/v1/pets", "/api/v1/pets/**").hasRole("ADMIN")
                        .anyRequest().denyAll());
        return http.build();
    }
}
