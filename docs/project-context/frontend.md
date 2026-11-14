# Frontend

## Stack and structure

- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x.
- All API requests target `VITE_API_GATEWAY_URL` (default `http://localhost:8089`).
- Source is grouped by feature: `app/` (routes and providers), `api/` (backend calls behind `api/index.js`), `features/{auth,catalog,cart,checkout,orders}` (each with a public `index.js`), `components/` (shared `Layout`) and `theme/`. Pages are found under their feature, e.g. `features/orders/OrdersPage.jsx`. Boundary rules are checked by `npm run check:boundaries`; see [Code structure](./code-structure.md#frontend-frontendsrc).

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
| `/orders` | `OrdersPage`: Displays user order history (`GET /api/v1/orders`), live saga status (`CREATED`, `PAYMENT_PENDING`, `CONFIRMED`, `FAILED`, `CANCELLED`; `PAID` no longer exists since Phase 2), shipment tracking numbers (`GET /api/v1/shipments/order/{id}`), confirmation notifications (`GET /api/v1/notifications/order/{id}`), and an order cancellation button (`PATCH /api/v1/orders/{id}/cancel`). Authenticated order/tracking requests include the gateway-issued bearer token. |
