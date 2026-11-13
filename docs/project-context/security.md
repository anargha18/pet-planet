# Security

## Gateway-owned RS256 Authentication & Authorization

- **`gateway-service` (`:8089`)**: Owns public registration/login orchestration, RS256 token signing/validation, and coarse route/role authorization. Tokens include `iss=pet-planet`, `aud=pet-planet`, `sub=email`, integer `userId`, and `role=ROLE_USER` or `ROLE_ADMIN`.
- **`user-service` (`:8088`, internal only)**: Owns BCrypt hashes and verifies credentials through gateway-only internal endpoints. It never signs or validates JWTs. Its internal endpoints require a shared `X-Internal-Service-Token`; Compose does not publish backend service ports to the host.
- **`pet-catalog-service`** and **`inventory-service`**: Retain JWT verification and role restrictions as defense in depth; the gateway also rejects unauthorized edge requests.
- **`order-service`**: Retains JWT verification and order ownership checks. It takes the signed integer `userId` claim as its customer identity instead of deriving an unrelated number from email.
- **Business-level authorization** remains with the owning service (such as order ownership); gateway checks are not a replacement for domain data checks.
- The Compose shared token is for local development. Production deployment should use a high-entropy secret or, preferably, mTLS between the gateway and user service; production mTLS is not configured in this repository.
- Deactivating a user prevents new login and profile access, but already-issued stateless JWTs remain valid until they expire (default one hour); immediate revocation is not implemented.

## CORS Architecture

- **Edge Gateway CORS (`gateway-service`, `:8089`)**: Serves as the single CORS entry point for frontend traffic (`allowedOriginPatterns: http://localhost:*, http://127.0.0.1:*`), supporting credentialed requests and handling preflight `OPTIONS` requests via `add-to-simple-url-handler-mapping: true`.
- **Response Header De-duplication**: Programmatic routes in `GatewayConfig` and global configuration apply `DedupeResponseHeader` (`RETAIN_UNIQUE`) to ensure exactly one clean `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials` header is delivered to the browser.
- **Downstream Services**: Delegate CORS handling to `gateway-service` to prevent duplicate cross-origin response headers. Only gateway and frontend HTTP ports are published by Compose; database and Kafka ports remain separately published for local development.
