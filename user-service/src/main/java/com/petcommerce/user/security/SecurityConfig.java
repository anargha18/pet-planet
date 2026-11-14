package com.petcommerce.user.security;

import com.petcommerce.platform.security.PlatformHttpSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * user-service accepts only gateway traffic (shared internal token, {@link InternalServiceKeyFilter}) and, like every
 * service, validates the caller's JWT itself (R-SEC-02). Login/registration are public; the self-service profile needs
 * any valid token; user administration needs ROLE_ADMIN.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PlatformHttpSecurity platform,
            InternalServiceKeyFilter internalServiceKeyFilter
    ) throws Exception {
        platform.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(internalServiceKeyFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
