# API Gateway

**Module:** `gateway-service` · **Package root:** `com.petcommerce.gateway` · **Port:** 8089.

- The only published application API entrypoint in the local Compose setup; it routes requests to all nine backend domain services.
- **Does not issue tokens.** `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are routed publicly to user-service, which signs the token. The gateway strips client identity headers from these requests and attaches `X-Internal-Service-Token`.
- Validates the JWT as a Spring Security **WebFlux OAuth2 resource server** (`security/SecurityConfig`): RS256 signature with the public key only, expiry, issuer, audience, numeric `sub` (user ID) and a known `role`, using the shared `PlatformJwt` contract from `platform-commons`. It does not use JJWT any more.
- Applies **coarse rules only**: catalog reads, login and registration are public; user administration, catalog writes, inventory and the two `…/delivered` endpoints require `ADMIN`; `/internal/**` is denied; every other `/api/v1/**` path requires a valid token. Full table in [security.md](../security.md).
- **No per-record ownership checks** (R-SEC-04): the synchronous order-service ownership callout and the customer-ID path check were removed. Each owning service decides ownership from the forwarded JWT.
- `security/ForwardedHeadersFilter` strips client-supplied `X-Internal-Service-Token` / `X-Authenticated-User-*` headers, forwards the JWT unchanged, and adds `X-Internal-Service-Token` on user-service routes (503 if it is not configured).
- `security/CorrelationIdWebFilter` assigns `X-Correlation-Id` to every request (forwarded and echoed).
- Owns the platform's only CORS policy (`security/CorsConfig` → `CorsConfigurationSource`, applied first in the security chain, so 401/403 also carry CORS headers). Allowed origins come from `FRONTEND_ORIGIN` (comma-separated; default `http://localhost:5173,http://127.0.0.1:5173`).
- 401/403 bodies use the shared `ApiError` shape.
- Packages: `routing` (route definitions), `security` (security chain, header filter, correlation ID, CORS).
- Tests: `GatewaySecurityTest` (real tokens, real routes, stub backend echoing headers), `CorsConfigTest`.
- The Compose internal token is suitable only for local development. Production service-to-service traffic should use mTLS or an equivalent workload identity mechanism.
