# Frontend

## Stack and structure

- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x. No frontend test framework yet.
- All API requests target `VITE_API_GATEWAY_URL` (default `http://localhost:8089`).
- Source is grouped by feature: `app/` (router, providers, routes, route guards), `api/` (backend calls behind `api/index.js`), `features/{auth,catalog,cart,checkout,orders,profile,admin}` (each with a public `index.js`), shared `components/` and `theme/`. Boundary rules are checked by `npm run check:boundaries`; see [Code structure](./code-structure.md#frontend-frontendsrc).
- The Compose `frontend` service runs the Vite dev server (R-FE-06: kept for learning). The image copies the source, so `docker compose up -d --build frontend` is needed after a change. No production `nginx` stage exists; R-FE-06 makes it optional.

## HTTP client and session (R-FE-01)

- **One axios instance** (`api/client.js`) for every call. A request interceptor attaches the bearer token, which the auth layer hands over with `setAccessToken`, so API functions never take a token argument.
- A response interceptor reacts to a **401 on a request that carried a token** (expired, revoked or forged): `AuthProvider` clears the session and redirects to `/login?expired=1&next=<current path>`. The login page then says "Your session has expired" and returns to `next` after sign-in. Only same-app paths are followed.
- `AuthProvider` also reads the JWT's `exp` claim (it does not verify the token; the backend does). A stored token that is already expired is dropped on load, and a timer ends the session when `exp` passes while the app is open.
- The session (`{ token, id, email, role }`, from the login/register `AuthResponse`) is kept in `localStorage` under `pet-planet-auth`. The cart is kept in `localStorage` under `pet-planet-cart`.
- Errors are shown with the backend's `message` from the shared error body (`extractApiError`). Loading, error and empty states are distinct components (`components/PageState`). A 404 from the tracking view right after checkout is shown as "not available yet", not as an error (R-FE-05).

## Routes and access (R-FE-02)

| Route | Access | Page / behavior |
|---|---|---|
| `/` | public | `HomePage`: featured pets and species cards. |
| `/pets` | public | `PetsPage`: paged catalog, species filter from `?species=`, name search, price sort. |
| `/pets/:id` | public | `PetDetailPage`: details, availability, add to cart. |
| `/login`, `/register` | public | Sign-in (shows the expired-session and "please sign in" notices) and registration with auto-login. |
| `/cart` | public | Browser-local cart. |
| `/checkout` | signed in (`ProtectedRoute`) | Shows that payment is simulated (DEC-4), submits `POST /api/v1/orders`, clears the cart and opens the new order's page. |
| `/orders` | signed in | `OrdersPage`: the user's orders (`GET /api/v1/orders`) with order, payment and delivery status from **one** call to the tracking projection (`GET /api/v1/fulfillment/orders/me`), and an escalation marker. |
| `/orders/:orderId` | signed in (owner, or any order for an admin) | `OrderDetailPage`: items, status card (order, simulated payment, shipment, delivery, tracking number), **timeline** from `GET /api/v1/fulfillment/orders/{id}`, all notifications (`GET /api/v1/notifications/order/{id}`), cancel button while `CREATED`/`PAYMENT_PENDING`. It **polls every 2 s** while the order is open or a confirmed order has not reached shipping yet, for at most 60 s, then asks the user to refresh (R-FE-03). |
| `/profile` | signed in | `ProfilePage`: view and edit name and email (`GET`/`PATCH /api/v1/users/me`), change password (`PUT /api/v1/users/me/password`). |
| `/admin` | `ADMIN` (`AdminRoute`; others see "Admins only") | Links to the admin pages. |
| `/admin/pets` | `ADMIN` | Catalog list with an availability filter (including archived `RETIRED`), create/edit dialog (no availability field), archive (409 messages shown). |
| `/admin/inventory` | `ADMIN` | Read-only inventory (`GET /api/v1/inventory/pets?status=`): status, order, reservation and expiry times, sale time. Pet names come from the catalog (best effort). |
| `/admin/orders` | `ADMIN` | All orders with status and customer filters; details open `/orders/:id`. |
| `/admin/users` | `ADMIN` | Paged users, create dialog (any role), role change, deactivate/reactivate (the last-admin 409 is shown). |
| `/admin/deliveries` | `ADMIN` | Orders that reached shipping (tracking projection, 50 per page, optional "escalated only"), with buttons for the allowed next delivery statuses (`PUT /api/v1/deliveries/order/{id}/status`; a failure note can be entered). |

The navigation adapts to the role: guests see Home and Browse Pets; signed-in users also see Orders and Profile; admins also see Admin. The backend enforces every rule independently; the route guards only avoid showing pages that would fail.

## Verification

- `npm run build` and `npm run check:boundaries` pass.
- `make ui-check` (`frontend/scripts/ui-check.mjs`, see [Testing](./testing.md#ui-walkthrough-make-ui-check)) passed 29/29 checks on 2026-11-15, with no JavaScript errors in the console: on the long-running stack and twice on a fresh clean checkout. It covers:
  - guest redirects and navigation;
  - customer login with return to the requested page;
  - "Admins only" for a customer;
  - profile;
  - add to cart → checkout (simulated-payment notice) → order page polling to `Confirmed`, with the shipment in the timeline and a notification;
  - a forged token → `/login?expired=1` with the session cleared;
  - an already-expired token dropped on load;
  - every admin page rendering;
  - an admin "Delivered" action reflected on the order page.
