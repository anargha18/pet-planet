package com.petcommerce.gateway.routing;

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

    @Value("${gateway.payment-service-url:http://localhost:8083}")
    private String paymentServiceUrl;

    @Value("${gateway.notification-service-url:http://localhost:8084}")
    private String notificationServiceUrl;

    @Value("${gateway.shipping-service-url:http://localhost:8085}")
    private String shippingServiceUrl;

    @Value("${gateway.delivery-service-url:http://localhost:8086}")
    private String deliveryServiceUrl;

    @Value("${gateway.fulfillment-service-url:http://localhost:8087}")
    private String fulfillmentServiceUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth", route -> route
                        .path("/api/v1/auth/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(userServiceUrl))
                .route("user-service", route -> route
                        .path("/api/v1/users", "/api/v1/users/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(userServiceUrl))
                .route("catalog-service", route -> route
                        .path("/api/v1/pets/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(petCatalogUrl))
                .route("order-service", route -> route
                        .path("/api/v1/orders/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(orderServiceUrl))
                .route("inventory-service", route -> route
                        .path("/api/v1/inventory/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(inventoryServiceUrl))
                .route("payment-service", route -> route
                        .path("/api/v1/payments/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(paymentServiceUrl))
                .route("notification-service", route -> route
                        .path("/api/v1/notifications/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(notificationServiceUrl))
                .route("shipping-service", route -> route
                        .path("/api/v1/shipments/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(shippingServiceUrl))
                .route("delivery-service", route -> route
                        .path("/api/v1/deliveries/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(deliveryServiceUrl))
                .route("fulfillment-service", route -> route
                        .path("/api/v1/fulfillment/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"))
                        .uri(fulfillmentServiceUrl))
                .build();
    }
}
