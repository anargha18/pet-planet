# User service

**Module:** `user-service` · **Package root:** `com.petcommerce.user` · **Database:** `petplanet_user` (configured name; live database not inspected) · **Port:** 8088 by default.

## Responsibilities and structure

- `controller/AuthController`: registration, login, current profile, admin user lookup.
- `service/UserService`: registration, password verification, user lookup, Spring `UserDetailsService`.
- `entity/User`, `Role`; `repository/UserRepository`.
- `security/JwtService`, `JwtAuthenticationFilter`.
- `config/SecurityConfig`: stateless filter chain, CORS, public route allow-list, authentication provider, BCrypt encoder.
- `dto/RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`.
- `exception/ApiExceptionHandler`: validation, argument/authentication, generic error responses.

## Behavior and security

Registration creates `CUSTOMER` users; passwords are BCrypt encoded. Login returns a signed bearer JWT with email subject and role claim. `/api/v1/users/me` requires authentication; `/api/v1/users/{id}` requires `ROLE_ADMIN`. Auth routes and docs/health/error paths are public according to SecurityConfig; unmatched requests require authentication. CORS allows one configured origin and selected methods/headers; CSRF is disabled; sessions are stateless.

User IDs are UUIDs. The order API expects a numeric `Long` customer ID; no identity bridge exists. JWT validation/security is not configured in other services. Full details are in [security.md](../security.md), [api-reference.md](../api-reference.md), and [database.md](../database.md).

