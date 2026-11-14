package com.petcommerce.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.platform.security.PlatformJwt;
import com.petcommerce.platform.web.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Edge security (DEC-1, R-SEC-02/04): the gateway validates the RS256 token with the public key and the shared
 * platform contract ({@link PlatformJwt}), then applies only <b>coarse</b> rules: public catalog reads and
 * login/registration, admin-only path prefixes, a valid token for everything else. Per-record ownership is decided by
 * the owning service, never here. {@code /internal/**} is never reachable from outside (R-SEC-05).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper
    ) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return write(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                    "Authentication required: missing, invalid or expired token");
        };
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> write(exchange, objectMapper, HttpStatus.FORBIDDEN, "Access denied");

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(PlatformJwt.authenticationConverter()))))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/v1/users/me", "/api/v1/users/me/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/pets", "/api/v1/pets/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/inventory", "/api/v1/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/shipments/order/*/delivered",
                                "/api/v1/deliveries/order/*/delivered").hasRole("ADMIN")
                        .pathMatchers("/api/v1/**").hasAnyRole("USER", "ADMIN")
                        .anyExchange().denyAll())
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${jwt.public-key:}") String publicKey,
            @Value("${jwt.issuer:pet-planet}") String issuer,
            @Value("${jwt.audience:pet-planet}") String audience
    ) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(PlatformJwt.parsePublicKey(publicKey))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(PlatformJwt.validator(issuer, audience));
        return decoder;
    }

    /** Writes the platform's shared {@link ApiError} body (R-SEC-06). */
    static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message,
                exchange.getRequest().getPath().value(), Instant.now(),
                exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
        try {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body))));
        } catch (Exception ex) {
            return Mono.error(ex);
        }
    }
}
