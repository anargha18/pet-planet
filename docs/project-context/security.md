# Security

## Spring Security and authorization

Only user-service has a Spring Security configuration. It enables method security, uses a stateless session policy, disables CSRF, configures CORS, and installs a JWT filter before the username/password filter.

- Public paths: `/api/v1/auth/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`, `/error`.
- Other user-service requests require authentication.
- `GET /api/v1/users/me` requires authenticated principal.
- `GET /api/v1/users/{id}` requires `ROLE_ADMIN`.
- Registration assigns `CUSTOMER`; no public role assignment was found.
- Roles present in entity enum: `CUSTOMER`, `ADMIN`.

The other eight services do not show Spring Security/JWT validation configuration. Their endpoints should not be assumed protected because a token is issued by user-service.

## Authentication and credentials

User-service uses BCrypt password hashing. Login/register return a signed bearer JWT. JWT contains email subject and role claim and is configured with expiration. The HMAC signing key is read from `JWT_SECRET`; expiry is read from `JWT_EXPIRATION_MS`. The request filter checks validity and loads user details from the database.

The React app stores the token/auth object in local storage and sends it as a bearer token to user-service profile calls (and generically exposes helper headers). It does not guard routes by role.

## CORS and CSRF

User-service allows one configured origin, with default origin configured for local frontend. Allowed methods are GET, POST, PUT, PATCH, DELETE, OPTIONS; allowed headers are `Authorization` and `Content-Type`. CSRF is disabled in this stateless bearer-token setup. No corresponding CORS or CSRF configuration was found in other services.

## Secret handling

Use environment variables for DB credentials and JWT key; this document lists variable names only. `.env.example` and application configs include development/example fallback values; do not treat them as production-safe. `.env` is ignored by Git and should remain local. No production secret manager, TLS termination, or key rotation setup was found.

## Observable gaps (not a penetration test)

- Business services do not validate tokens or enforce roles; catalog mutation endpoints have no visible authorization rules.
- Order API accepts client-supplied numeric `customerId` without tying it to authenticated identity.
- User-service uses UUID IDs while order API expects numeric IDs; the frontend's conversion does not establish a safe identity mapping.
- Local Kafka listeners use plaintext and local broker has a single replica.
- No TLS/production secret-management/deployment controls were found.
- Payment is simulated, not a real payment integration.

These are source-visible configuration gaps, not claims that a live deployed system is exploitable. Runtime/deployment topology was not verified. See [implementation status](./implementation-status.md).

