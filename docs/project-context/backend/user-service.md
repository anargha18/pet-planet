# User service

**Module:** `user-service` · **Package root:** `com.petcommerce.user` · **Database:** `petplanet_user` · **Internal port:** 8088 (not published by Compose).

- Owns user records with generated integer IDs, BCrypt password hashes, `USER`/`ADMIN` roles, account status, and profile timestamps.
- The gateway exposes public registration/login and forwards authenticated user-management requests. The user service has internal credential-verification and registration endpoints protected by `X-Internal-Service-Token`; it does not issue or validate JWTs.
- User APIs support self profile lookup/edit and password change, plus admin paging, creation, lookup, partial update, and soft deactivation.
- `V2__seed_users.sql` seeds the initial accounts; `V3__integer_user_ids_and_account_status.sql` converts IDs to an identity column, adds account status, and migrates `CUSTOMER` to `USER`.
- `USER_SERVICE_INTERNAL_TOKEN` must be shared only by the gateway and user service. Backend service ports are not published by the local Compose setup.
