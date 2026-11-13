package com.petcommerce.gateway.api.controller;

import com.petcommerce.gateway.api.dto.AuthResponse;
import com.petcommerce.gateway.api.dto.LoginRequest;
import com.petcommerce.gateway.api.dto.RegisterRequest;
import com.petcommerce.gateway.api.dto.UserResponse;
import com.petcommerce.gateway.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final WebClient userServiceClient;
    private final JwtService jwtService;

    public AuthController(
            WebClient.Builder webClientBuilder,
            JwtService jwtService,
            @Value("${gateway.user-service-url}") String userServiceUrl,
            @Value("${gateway.user-service-internal-token:}") String internalServiceToken
    ) {
        this.userServiceClient = webClientBuilder
                .baseUrl(userServiceUrl)
                .defaultHeader("X-Internal-Service-Token", internalServiceToken)
                .build();
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return requestUserService("/internal/users/register", request)
                .map(this::createAuthResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return requestUserService("/internal/users/authenticate", request)
                .map(this::createAuthResponse);
    }

    private <T> Mono<UserResponse> requestUserService(String path, T request) {
        return userServiceClient.post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> Mono.error(new ResponseStatusException(response.statusCode())))
                .bodyToMono(UserResponse.class);
    }

    private AuthResponse createAuthResponse(UserResponse user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                user.id(),
                user.email(),
                user.role()
        );
    }
}
