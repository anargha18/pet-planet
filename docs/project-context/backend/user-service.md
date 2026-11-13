# User service

**Module:** `user-service` · **Package root:** `com.petcommerce.user` · **Database:** `petplanet_user` · **Internal port:** 8088 (not published by Compose).

- Owns user records with generated integer IDs, BCrypt password hashes, `USER`/`ADMIN` roles, account status, and profile timestamps.
- **Issues access tokens.** `POST /api/v1/auth/register` and `POST /api/v1/auth/login` (`api/controller/AuthController` → `application/AuthService`) verify credentials and return an `AuthResponse` with an RS256 JWT signed by `security/token/TokenIssuer`. user-service is the only service holding `JWT_PRIVATE_KEY`. Profile and admin account management live in `application/UserService`.
- Every request (including `/api/v1/auth/**`) must carry the gateway's `X-Internal-Service-Token`; the service does not validate JWTs itself (the gateway passes the trusted `X-Authenticated-User-Id`).
- User APIs support self profile lookup/edit and password change, plus admin paging, creation, lookup, partial update, and soft deactivation.
- `V2__seed_users.sql` seeds the initial accounts; `V3__integer_user_ids_and_account_status.sql` converts IDs to an identity column, adds account status, and migrates `CUSTOMER` to `USER`; `V4__fix_seed_account_passwords.sql` replaces the V2 seed hash (which did not match the documented password) with a verified BCrypt hash of `password123`, checked by `SeedAccountPasswordTest`.
- `USER_SERVICE_INTERNAL_TOKEN` must be shared only by the gateway and user service. Backend service ports are not published by the local Compose setup.
