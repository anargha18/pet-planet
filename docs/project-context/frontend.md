# Frontend

## Stack and structure

- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x.
- All API requests target `VITE_API_GATEWAY_URL` (default `http://localhost:8089`).

## Routes and pages

| Route | Page / behavior |
|---|---|
| `/` | `HomePage`: Featured pets from catalog and species category cards (`/pets?species=CAT`, `/pets?species=DOG`). |
| `/pets` | `PetsPage`: Paged catalog, species filter (initialized from `?species=` query param via `useSearchParams`), client-side name search and price sort, and 1-indexed MUI `<Pagination page={page + 1}>`. |
| `/pets/:id` | `PetDetailPage`: Pet details, live availability badge (`AVAILABLE`, `RESERVED`, `SOLD`), and add-to-cart. |
| `/login` | `LoginPage`: Authenticates via `/api/v1/auth/login` and stores JWT in `AuthContext` (`localStorage`). |
| `/register` | `RegisterPage`: Registers via `/api/v1/auth/register`, automatically logs the user in using the returned `AuthResponse` token, and redirects to `/`. |
| `/cart` | `CartPage`: Browser-local cart and subtotal summary. |
| `/checkout` | `CheckoutPage`: Submits authenticated order (`POST /api/v1/orders`), clears cart, and links to `/orders` ("View Order & Tracking"). |
| `/orders` | `OrdersPage`: Displays customer order history (`GET /api/v1/orders`), live saga status (`CREATED`, `PAYMENT_PENDING`, `PAID`, `CONFIRMED`, `FAILED`, `CANCELLED`), shipment tracking numbers (`GET /api/v1/shipments/order/{id}`), confirmation notifications (`GET /api/v1/notifications/order/{id}`), and an order cancellation button (`PATCH /api/v1/orders/{id}/cancel`). |
