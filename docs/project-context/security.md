# Security

One authentication model for the whole platform (Phase 3: R-SEC-02, R-SEC-04…06, R-INF-08). The shared pieces live in `platform-commons` (`com.petcommerce.platform.security` and `com.petcommerce.platform.web`); each service keeps only its own URL rules and ownership checks.

## Tokens (RS256, issued only by user-service)

- **Issuer:** `user-service` (`security/token/TokenIssuer`) signs tokens with Spring Security's `NimbusJwtEncoder` and the private key `JWT_PRIVATE_KEY`. No other service receives the private key.
- **Claims:** `sub` = integer user ID (as a string), `email`, `role` = `ROLE_USER` or `ROLE_ADMIN`, `iss` = `JWT_ISSUER` (default `pet-planet`), `aud` = `JWT_AUDIENCE` (default `pet-planet`), `iat`, `exp` (`JWT_EXPIRATION_MS`, default one hour).
- **Verification (every HTTP service, gateway included):** `spring-boot-starter-oauth2-resource-server` with the public key `JWT_PUBLIC_KEY`. The token contract is in `PlatformJwt`:
  - accepted only when signed by the key pair (RS256), not expired, with the expected issuer and audience, a positive numeric `sub`, and a known `role`;
  - claim mapping: the authentication's **name is the user ID**; its single authority is the `role` claim (`ROLE_USER`/`ROLE_ADMIN`), so `hasRole("ADMIN")` works everywhere.
- Servlet services get the decoder from `PlatformSecurityAutoConfiguration` (`jwt.public-key`, `jwt.issuer`, `jwt.audience`); the gateway builds the reactive equivalent in `security/SecurityConfig` from the same `PlatformJwt` rules.
- `TokenIssuerTest` proves a user-service token passes the platform validator; `PlatformSecurityTest` covers forged, expired, wrong-issuer, wrong-audience, non-numeric-subject and unknown-role tokens (all 401).
- Tokens are stateless: deactivating a user blocks new logins and `/users/me`, but already-issued tokens stay valid until they expire. Immediate revocation is not implemented.

## Gateway (`gateway-service`, `:8089`): coarse rules only

`security/SecurityConfig` (Spring Security WebFlux) validates the token and applies:

| Rule | Paths |
|---|---|
| Public | `OPTIONS /**`, `/actuator/health/**`, `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `GET /api/v1/pets/**` |
| Never reachable (R-SEC-05) | `/internal/**` (denied; also not routed) |
| `USER` or `ADMIN` | `/api/v1/users/me/**`, and every other `/api/v1/**` path |
| `ADMIN` only | `/api/v1/users/**`, non-GET `/api/v1/pets/**`, `/api/v1/inventory/**`, `PUT /api/v1/deliveries/order/*/status` |
| Denied | anything else |

- **No ownership decisions at the gateway** (R-SEC-04). The former synchronous order-service ownership callout (`GatewayAuthFilter.forwardIfOrderOwned`) and the customer-ID path check were removed; requests for another customer's records are forwarded and rejected by the owning service.
- `security/ForwardedHeadersFilter` removes client-supplied `X-Internal-Service-Token` and `X-Authenticated-User-*` headers from every routed request and adds the shared `X-Internal-Service-Token` on user-service routes. The JWT itself is forwarded unchanged; downstream services trust only the JWT.
- `security/CorrelationIdWebFilter` gives every request an `X-Correlation-Id` (a short, safe client value is kept) that is forwarded downstream and echoed in the response.
- Verified by `GatewaySecurityTest` (real tokens, real routes, a stub backend that echoes received headers).

## Services: authentication and ownership

- Each servlet service has `security/SecurityConfig`, which calls `PlatformHttpSecurity.apply(http)` (stateless, CSRF/CORS/basic/form login off, JWT resource server, JSON 401/403) and then declares its own rules, ending with `anyRequest().denyAll()`. `PlatformHttpSecurity.PUBLIC_PATHS` (health, `/error`, OpenAPI docs) is open.

| Service | Rules | Ownership check |
|---|---|---|
| user-service | `POST /api/v1/auth/login|register` public; `/api/v1/users/me/**` any valid token; `/api/v1/users/**` `ADMIN` | `/me` endpoints use the user ID from the token (`AuthenticatedCaller`), never a header |
| pet-catalog-service | `GET /api/v1/pets/**` public; other `/api/v1/pets/**` `ADMIN` | — |
| order-service | `/api/v1/orders/**` `USER`/`ADMIN` | `security/OrderAccessPolicy`: users see and cancel only their own orders; list scoped to the caller |
| inventory-service | `/api/v1/inventory/**` `ADMIN` | — |
| payment-service | `GET /api/v1/payments/**` `USER`/`ADMIN` | `PaymentService.getForOrder`: payment `customer_id` must equal the caller (payments created before `V4` have no owner: admin only) |
| notification-service | `GET /api/v1/notifications/**` `USER`/`ADMIN` | `getForOrder` (every returned notification must be the caller's) / `getForCustomer` (also `/me`) |
| shipping-service | `GET /api/v1/shipments/**` `USER`/`ADMIN`; nothing else | `getForOrder` / `getForCustomer` |
| delivery-tracking-service | `GET /api/v1/deliveries/**` `USER`/`ADMIN`; `PUT /api/v1/deliveries/order/*/status` `ADMIN` | `getForOrder` / `getForCustomer` |
| fulfillment-service | `GET /api/v1/fulfillment/**` `USER`/`ADMIN` | `getForOrder`; `getMine` (token user); `search` admin only (`requireAdmin`) |

- **`AuthenticatedCaller`** (platform-commons) is resolved as a controller parameter from the validated token: `userId`, `email`, `admin`. `requireAccessTo(customerId)` passes for admins and for the record's owner, otherwise throws `AccessDeniedException` → **403**. A record with no known owner is visible to admins only.
- Missing records return **404**, not 500. Foreign records return **403** (as order-service already did); this reveals whether an order ID exists, which is accepted for this project.
- Verified per service by `*OwnershipTest` (owner/foreign/admin/unknown owner/not found) and `*ControllerTest` (real tokens: 401 without or with a forged token, the token subject reaches the check, shared error bodies, admin-only delivery status change), and live by the smoke test's security scenario.

## user-service internal token

- user-service accepts only gateway traffic: `InternalServiceKeyFilter` requires the shared `X-Internal-Service-Token` on every request except health checks (constant-time comparison), before JWT validation. A missing token gives a 401 `ApiError`; an unconfigured service gives 503.
- `USER_SERVICE_INTERNAL_TOKEN` is generated into `.env` by `scripts/generate-dev-keys.sh`; Compose has no fallback value. Production should use mTLS or workload identity instead (not configured here).

## Error body (R-SEC-06)

Every service and the gateway return the same JSON for errors (`platform-commons` `ApiError`):

```json
{ "status": 403, "error": "Forbidden", "message": "Access denied", "path": "/api/v1/payments/order/7",
  "timestamp": "2026-11-14T10:00:00Z", "correlationId": "5f0c…" }
```

- `PlatformExceptionHandler` (lowest precedence) maps shared technical errors: validation and malformed JSON → 400, `AccessDeniedException` → 403, unknown path → 404, unsupported method → 405, anything else → 500 (logged).
- Each service's `api/error` advice (declared with `@Order(Ordered.HIGHEST_PRECEDENCE)`) maps its domain exceptions, e.g. `*NotFoundException` → 404, invalid state / not archivable / pet unavailable / last admin / duplicate email → 409, catalog unavailable → 503, bad credentials → 401.
- 401/403 written by Spring Security (`PlatformHttpSecurity`, gateway `SecurityConfig`) and by `InternalServiceKeyFilter` use the same body.
- `correlationId` comes from `X-Correlation-Id` (`CorrelationIdFilter` in services, put into the logging MDC as `correlationId`; `CorrelationIdWebFilter` at the gateway).

## Secrets (R-INF-08)

- No keys or tokens are committed. `.env.example` has empty `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY` and `USER_SERVICE_INTERNAL_TOKEN`; `scripts/generate-dev-keys.sh` (run by `make up`, `make infra` and `make keys`) creates `.env` if needed and fills them with a fresh RSA-2048 pair and a random token. Existing values are kept unless `--force` is passed.
- `application.yml` files have no key defaults; a service started without `JWT_PUBLIC_KEY` fails at startup with a message pointing to the script.
- The development key pair committed before Phase 3 is still in git **history** (history was not rewritten). It was only ever a local development key; regenerate your `.env` with `scripts/generate-dev-keys.sh --force` if it still contains it.

## CORS

- Only the gateway does CORS (R-SEC-03). `CorsConfig` provides the `CorsConfigurationSource` (origins from `FRONTEND_ORIGIN`, default `http://localhost:5173,http://127.0.0.1:5173`; credentials allowed; `X-Correlation-Id` exposed), and `SecurityConfig` applies it as the first filter of the security chain. Preflights are answered for every path, and 401/403 responses also carry CORS headers, so the browser can read them.
- Routes keep `DedupeResponseHeader` for `Access-Control-Allow-Origin`/`-Credentials`. Downstream services disable CORS (`PlatformHttpSecurity`). Verified by `CorsConfigTest` and `GatewaySecurityTest`.
