# User service

**Module:** `user-service` · **Package root:** `com.petcommerce.user` · **Database:** `petplanet_user` · **Port:** 8088.

- Manages registration (`POST /api/v1/auth/register`), login (`POST /api/v1/auth/login`), current profile (`GET /api/v1/users/me`), and admin user lookup (`GET /api/v1/users/{id}`).
- Uses BCrypt password hashing and RS256 asymmetric JWT signing (`JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`).
- `V2__seed_users.sql` seeds `admin@petplanet.com` (`ADMIN`) and `customer@petplanet.com` (`CUSTOMER`) with password `password123`.
