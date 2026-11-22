// Where the stack runs and who signs in. Override the URLs with environment variables if your ports differ.
export const APP_URL = process.env.APP_URL || 'http://localhost:5173';
export const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8089';

// Seeded by user-service Flyway migrations V2/V4 (see docs/06-database.md).
export const PASSWORD = 'password123';
export const CUSTOMER = { email: 'customer@petplanet.com', storageState: 'e2e/.auth/customer.json' };
export const ADMIN = { email: 'admin@petplanet.com', storageState: 'e2e/.auth/admin.json' };

// localStorage keys used by the storefront (features/auth/AuthContext.jsx, features/cart/CartContext.jsx).
export const AUTH_STORAGE_KEY = 'pet-planet-auth';
