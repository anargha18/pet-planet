package com.petcommerce.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${gateway.user-service-url:http://localhost:8088}")
    private String userServiceUrl;

    @Value("${gateway.pet-catalog-url:http://localhost:8080}")
    private String petCatalogUrl;

    @Value("${gateway.order-service-url:http://localhost:8081}")
    private String orderServiceUrl;

    @Value("${gateway.inventory-service-url:http://localhost:8082}")
    private String inventoryServiceUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", route -> route
                        .path("/api/v1/auth/**")
                        .uri(userServiceUrl))
                .route("user-service", route -> route
                        .path("/api/v1/users/**")
                        .uri(userServiceUrl))
                .route("catalog-service", route -> route
                        .path("/api/v1/pets/**")
                        .uri(petCatalogUrl))
                .route("order-service", route -> route
                        .path("/api/v1/orders/**")
                        .uri(orderServiceUrl))
                .route("inventory-service", route -> route
                        .path("/api/v1/inventory/**")
                        .uri(inventoryServiceUrl))
                .build();
    }
}
