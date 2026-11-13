# API Gateway

**Module:** `gateway-service` · **Package root:** `com.petcommerce.gateway` · **Port:** 8089.

- The only published application API entrypoint in the local Compose setup; it routes requests to all nine backend domain services.
- Handles public `POST /api/v1/auth/register` and `POST /api/v1/auth/login`. It delegates registration and BCrypt credential verification to the internal user service, then issues an RS256 JWT.
- Validates JWT signature, issuer, audience, expiry, integer `userId`, and role for protected routes. The token carries the email as `sub`, `userId`, and `ROLE_USER` or `ROLE_ADMIN`.
- Enforces edge access rules: catalog reads are public; catalog writes and inventory require `ADMIN`; orders require `USER` or `ADMIN`; user profile routes require an authenticated role and admin CRUD routes require `ADMIN`; customer-specific lists and tracking lookups are owner-scoped; other application routes require a valid token.
- Removes client-provided `X-Authenticated-User-*` headers and writes trusted claims into downstream headers. It adds `X-Internal-Service-Token` on user-service requests.
- Downstream services retain JWT and domain-level checks as defense in depth. In particular, order-service enforces order ownership.
- The Compose internal token is suitable only for local development. Production service-to-service traffic should use mTLS or an equivalent workload identity mechanism.
