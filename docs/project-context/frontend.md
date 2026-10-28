# Frontend

## Stack and structure

- React and React DOM 18.3.1, JavaScript/JSX, React Router 6.28.0, Vite 5.4.10, `@vitejs/plugin-react` 4.3.2.
- UI: Material UI 5.16.x, MUI icons, Emotion 11.11.x. HTTP: Axios 1.7.x.
- The app lives in `frontend/`: `frontend/index.html` and `frontend/src/main.jsx` bootstrap it; `frontend/src/App.jsx` defines routes; `frontend/src/components/Layout.jsx` provides navigation/layout; `frontend/src/pages/` contains screen components; `frontend/src/context/` contains auth/cart state; `frontend/src/services/api.js` owns API calls; `frontend/src/theme/theme.js` and `frontend/src/index.css` define styles.
- Version numbers come from declared package ranges; lockfile is present, but installed dependency versions were not verified.

## Routes and pages

| Route | Page / behavior |
|---|---|
| `/` | Home, featured pets from catalog and species category links |
| `/pets` | Paged catalog, species filter, name search on currently loaded page, local price sorting |
| `/pets/:id` | Pet detail and add-to-cart |
| `/login` | Login form |
| `/register` | Registration form |
| `/cart` | Browser-local cart and subtotal |
| `/checkout` | Submits order to order-service |
| `*` | Renders home page |

## APIs and configuration

`src/services/api.js` creates three Axios instances (catalog/order/user), each with 20-second timeout. Base URLs come from:

- `VITE_PET_CATALOG_URL` (fallback `http://localhost:8080`)
- `VITE_ORDER_URL` (fallback `http://localhost:8081`)
- `VITE_USER_URL` (fallback `http://localhost:8088`)

The service exports pet listing/detail, auth register/login, current profile, and order creation helpers. Authorization headers include a bearer token when available. Error parsing checks common API error/message/details fields and otherwise uses the thrown error message.

## State, forms, and UI

- `AuthContext` persists the auth object under local-storage key `pet-planet-auth`; logout clears it. `refreshProfile` calls `/api/v1/users/me`; on request failure it clears local auth state. No route guard or role-based UI is present.
- `CartContext` persists `pet-planet-cart`, allows one entry per pet ID, calculates subtotal from stored prices, and clears/removes entries locally. No backend cart exists.
- Login/register/checkout forms use Material UI fields and client state. Login and register disable native validation with `noValidate`; server-side bean validation is relied on. Checkout includes an editable `customerId`.
- Material UI theme and global styles set palette, typography, cards, buttons, and responsive layouts.
- Catalog pages expose loading/error/empty states; auth and checkout expose error/submission states.

## Integration status and limitations

| Feature | UI integration |
|---|---|
| Catalog | Connected to catalog listing and detail APIs. |
| Registration/login | Connected to user-service. Registration ignores returned token and navigates to login. Login stores token/role/email. |
| Cart | Local browser storage only. No server API. |
| Checkout/order | Calls `POST /api/v1/orders`; success response is shown and local cart is cleared. |
| Profile | Checkout fetches current user ID if token is present. |
| Inventory/payment/notification/shipping/delivery/fulfillment | No frontend API use found. |
| Order history/tracking/admin | No UI found. |

Important mismatches: user profile ID is UUID, while order `customerId` expects a number; checkout converts the UUID to `Number`, which is not a valid identity mapping. Catalog response has no image property, although pages check `pet.image` and use Unsplash fallback images. Catalog search for name is client-side over the current page; no backend name query parameter exists. Home category links include `?species=...`, but the pets page does not read that query parameter, so those links do not initialize the species filter.

No frontend test or lint script is defined in `package.json`. See [implementation-status.md](./implementation-status.md) for overall status and [api-reference.md](./api-reference.md) for backend contracts.
