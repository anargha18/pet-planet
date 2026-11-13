-- The hash seeded in V2 did not match the documented demo password, so the seeded accounts could not log in.
-- Re-seed both demo accounts with a verified BCrypt hash of 'password123' (verified by SeedAccountPasswordTest).
-- Only rows still holding the broken V2 hash are touched, so a password changed by the user is preserved.
UPDATE users
SET password_hash = '$2a$10$.GOOshVGONC1GsAmBqTHSOzcCQaW8OWGoVnG2QTM8eH18JtmtoLwm',
    updated_at = NOW()
WHERE email IN ('admin@petplanet.com', 'customer@petplanet.com')
  AND password_hash = '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a';
