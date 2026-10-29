# Security

## RS256 Asymmetric JWT Authentication & Authorization

- **`user-service` (`:8088`)**: Authenticates users with BCrypt and signs JWTs using **RS256** (`PKCS#8` RSA private key from `JWT_PRIVATE_KEY`) with `iss=pet-planet`, `aud=pet-planet`, `sub=email`, and `role=ROLE_CUSTOMER` or `ROLE_ADMIN`.
- **`pet-catalog-service` (`:8080`)**: Verifies JWTs using the X.509 RSA public key (`JWT_PUBLIC_KEY`). `GET /api/v1/pets/**` is public; `POST`, `PUT`, and `DELETE` require `ROLE_ADMIN`.
- **`order-service` (`:8081`)**: Verifies JWTs using `JWT_PUBLIC_KEY`. All `/api/v1/orders/**` routes require `ROLE_CUSTOMER` or `ROLE_ADMIN`. Customers can only view or cancel their own orders (enforced via deterministic SHA-256 `customerId` derivation from the authenticated principal), while `ADMIN` can inspect any order.
- **`inventory-service` (`:8082`)**: Verifies JWTs using `JWT_PUBLIC_KEY`. Direct REST calls to `/api/v1/inventory/**` require `ROLE_ADMIN`.

## CORS Architecture

- **Edge Gateway CORS (`gateway-service`, `:8089`)**: Serves as the single CORS entry point for frontend traffic (`allowedOriginPatterns: http://localhost:*, http://127.0.0.1:*`), supporting credentialed requests and handling preflight `OPTIONS` requests via `add-to-simple-url-handler-mapping: true`.
- **Response Header De-duplication**: Programmatic routes in `GatewayConfig` and global configuration apply `DedupeResponseHeader` (`RETAIN_UNIQUE`) to ensure exactly one clean `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials` header is delivered to the browser.
- **Downstream Services**: Delegate CORS handling to `gateway-service` to prevent duplicate cross-origin response headers.
