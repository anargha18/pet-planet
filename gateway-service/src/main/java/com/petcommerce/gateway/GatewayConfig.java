package com.petcommerce.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
        RouteLocatorBuilder.Builder routes = builder.routes()
                .defaultFilters(filters -> filters.dedupeResponseHeader(
                        "Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_UNIQUE"));

        List.of(
                new GatewayRoute("auth-service", "/api/v1/auth/**", userServiceUrl),
                new GatewayRoute("user-service", "/api/v1/users/**", userServiceUrl),
                new GatewayRoute("catalog-service", "/api/v1/pets/**", petCatalogUrl),
                new GatewayRoute("order-service", "/api/v1/orders/**", orderServiceUrl),
                new GatewayRoute("inventory-service", "/api/v1/inventory/**", inventoryServiceUrl),
                new GatewayRoute("payment-service", "/api/v1/payments/**", paymentServiceUrl),
                new GatewayRoute("notification-service", "/api/v1/notifications/**", notificationServiceUrl),
                new GatewayRoute("shipping-service", "/api/v1/shipments/**", shippingServiceUrl),
                new GatewayRoute("delivery-service", "/api/v1/deliveries/**", deliveryServiceUrl),
                new GatewayRoute("fulfillment-service", "/api/v1/fulfillment/**", fulfillmentServiceUrl)
        ).forEach(route -> routes.route(route.id(), predicate -> predicate
                .path(route.path())
                .uri(route.uri())));

        return routes.build();
    }

    private record GatewayRoute(String id, String path, String uri) {
    }
}
