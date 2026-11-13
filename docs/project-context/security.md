# Security

## RS256 Authentication & Authorization

- **`user-service` (`:8088`, internal only)**: Issues tokens. Owns BCrypt hashes, verifies credentials on `POST /api/v1/auth/login` / `register`, and signs RS256 JWTs with the private key (`JWT_PRIVATE_KEY`, given to no other service). All its endpoints require the gateway's shared `X-Internal-Service-Token`; Compose does not publish backend service ports to the host.
- **`gateway-service` (`:8089`)**: Only validates tokens (public key), applies coarse route/role authorization, and routes. Login and registration pass through it publicly. Tokens include `iss=pet-planet`, `aud=pet-planet`, `sub=email`, integer `userId`, and `role=ROLE_USER` or `ROLE_ADMIN`.
- **`pet-catalog-service`** and **`inventory-service`**: Retain JWT verification and role restrictions as defense in depth; the gateway also rejects unauthorized edge requests.
- **`order-service`**: Retains JWT verification and order ownership checks. It takes the signed integer `userId` claim as its customer identity instead of deriving an unrelated number from email.
- **Business-level authorization** remains with the owning service (such as order ownership); gateway checks are not a replacement for domain data checks.
- The Compose shared token is for local development. Production deployment should use a high-entropy secret or, preferably, mTLS between the gateway and user service; production mTLS is not configured in this repository.
- Deactivating a user prevents new login and profile access, but already-issued stateless JWTs remain valid until they expire (default one hour); immediate revocation is not implemented.

## CORS Architecture

- **Edge Gateway CORS (`gateway-service`, `:8089`)**: The single CORS policy, implemented as a `CorsWebFilter` that runs before routing and authentication, so preflight `OPTIONS` requests are answered for every path, including `/api/v1/auth/**`. Allowed origins come from `FRONTEND_ORIGIN` (default `http://localhost:5173,http://127.0.0.1:5173`); credentialed requests are supported. Verified by `CorsConfigTest`.
- **Response Header De-duplication**: Programmatic routes in `GatewayConfig` and global configuration apply `DedupeResponseHeader` (`RETAIN_UNIQUE`) to ensure exactly one clean `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials` header is delivered to the browser.
- **Downstream Services**: Configure no CORS (`.cors(disable)` in catalog, order and inventory); the gateway handles it to prevent duplicate cross-origin response headers. Only gateway and frontend HTTP ports are published by Compose; database and Kafka ports remain separately published for local development.
