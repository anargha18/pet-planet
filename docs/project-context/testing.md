# Testing

## Frameworks and test inventory

Java modules declare Spring Boot Test. User-service additionally declares Spring Security Test. The repository has nine Java test files:

| Module | Test files / observed style |
|---|---|
| `pet-catalog-service` | `PetServiceTest`; JUnit Jupiter + Mockito extension |
| `order-service` | `OrderServiceTest`; JUnit Jupiter + Mockito; `OrderControllerTest` uses `@WebMvcTest` and MockMvc |
| `inventory-service` | `InventoryServiceTest`; JUnit Jupiter + Mockito |
| `payment-service` | `PaymentServiceTest`; JUnit Jupiter + Mockito |
| `notification-service` | `NotificationServiceTest`; JUnit Jupiter + Mockito |
| `shipping-service` | `ShippingServiceTest`; JUnit Jupiter + Mockito |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest`; JUnit Jupiter + Mockito |
| `fulfillment-service` | `FulfillmentServiceTest`; JUnit Jupiter + Mockito |
| `user-service` | No test source found in inspected tree |

No frontend test files or frontend test script were found. No broker-backed, database integration, end-to-end, or cross-service workflow test was found in enumerated test files. Coverage percentages were not available.

## Commands

Each module is independently built; the following commands are inferred from Maven configuration and were not run:

```sh
# From a backend service directory
mvn test
mvn package
```

The Docker backend build executes `mvn -B package -DskipTests`. The frontend `package.json` defines `dev`, `build`, and `preview`; there is no test script. See [infrastructure.md](./infrastructure.md).

## Verification status and gaps

Tests/builds were not run for this documentation task; pass/fail status is unknown. Important test gaps include the complete order → reservation → payment → confirmation → shipping/tracking/fulfillment lifecycle, duplicate/retry/outbox delivery, payment/reservation failure and compensation, authorization across services, customer ID compatibility, actual Flyway + Hibernate validation, and frontend/API integration.

