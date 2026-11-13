# API Gateway

**Module:** `gateway-service` · **Package root:** `com.petcommerce.gateway` · **Port:** 8089.

- The only published application API entrypoint in the local Compose setup; it routes requests to all nine backend domain services.
- **Does not issue tokens.** `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are routed publicly to user-service, which signs the token. The gateway strips client identity headers from these requests and attaches `X-Internal-Service-Token`.
- Validates JWT signature (public key only), issuer, audience, expiry, integer `userId`, and role for protected routes (`security/JwtService`). The token carries the email as `sub`, `userId`, and `ROLE_USER` or `ROLE_ADMIN`.
- Owns the platform's only CORS policy: a `CorsWebFilter` (`security/CorsConfig`) that answers preflight for every path. Allowed origins come from `FRONTEND_ORIGIN` (comma-separated; default `http://localhost:5173,http://127.0.0.1:5173`).
- Packages: `routing` (route definitions), `security` (auth filter, JWT validation, CORS).
- Enforces edge access rules: catalog reads are public; catalog writes and inventory require `ADMIN`; orders require `USER` or `ADMIN`; user profile routes require an authenticated role and admin CRUD routes require `ADMIN`; customer-specific lists and tracking lookups are owner-scoped; other application routes require a valid token.
- Removes client-provided `X-Authenticated-User-*` headers and writes trusted claims into downstream headers. It adds `X-Internal-Service-Token` on user-service requests.
- Downstream services retain JWT and domain-level checks as defense in depth. In particular, order-service enforces order ownership.
- The Compose internal token is suitable only for local development. Production service-to-service traffic should use mTLS or an equivalent workload identity mechanism.
