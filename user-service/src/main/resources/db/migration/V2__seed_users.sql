INSERT INTO users (id, email, password_hash, first_name, last_name, role, created_at, updated_at)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'admin@petplanet.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Admin', 'User', 'ADMIN', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000002', 'customer@petplanet.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Jane', 'Customer', 'CUSTOMER', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

