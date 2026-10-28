# Implementation status

## Status definitions

- **Implemented:** code/configuration for the capability exists. This does not imply runtime or end-to-end verification.
- **Partially implemented:** some capability exists, but important business wiring, integration, or lifecycle is absent.
- **Planned:** explicitly identified as future work in repository evidence. No formal roadmap was found.
- **Unverified:** not checked against a live database/runtime/build/test.

## Feature status

| Feature | Status | Evidence and qualification |
|---|---|---|
| Pet catalog CRUD/search | Implemented | Catalog controller/service/entity/repository/migration exist. No catalog authorization configuration. |
| Order create/list/get/cancel | Implemented, partial workflow | Order persists item snapshots and synchronously validates catalog pets. Does not publish the event that starts inventory. |
| Inventory reserve/expiry/sold | Implemented as service capability | Kafka consumers, REST operations, optimistic version field, expiration scheduler, migration. Checkout does not trigger reservation. |
| Payment | Implemented simulation | Consumes reserved event, fixed amount, configurable simulated result; no gateway or payment REST API. |
| Notifications | Implemented as local records | Confirmed event creates record and immediately marks it sent; no external delivery. |
| Shipping/delivery | Partially implemented | Local tracking/shipment/delivery records and manual delivered operation; no carrier integration. |
| Fulfillment | Partially implemented | Consumes delivery status and creates a record; later status updates do not update existing record. |
| User registration/login/JWT | Implemented in user-service | BCrypt/JWT and protected profile/admin endpoints; other services do not validate tokens. |
| React storefront | Present, partly integrated | Catalog/auth/order calls and local cart. Downstream APIs are not used in UI. |
| Live database schema | Unknown | Migrations were inspected; live DB and Flyway history were not inspected. |
| Runtime/build/test status | Unverified | Services, containers, builds, and tests were not run. |

## Critical workflow and integration gaps

1. **Order-to-inventory event missing.** `OrderService.create` persists and returns the order but does not publish `order-created-events`. `InventoryEventConsumer` listens on this topic, and no producer was found. The checkout pathway therefore does not start reservation or payment.
2. **Customer identity incompatible.** User ID is UUID; order request and database use numeric `Long`. Frontend converts current profile ID with `Number`, which is not a valid mapping. Order trusts client-supplied ID; downstream projections use fixed customer IDs.
3. **Payment simulation disconnected and fixed amount.** Payment consumes `pet-reserved-events`, sets a constant amount/USD, and does not use order total or payment provider.
4. **Incomplete compensation.** Payment failure marks order failed but does not release inventory; cancellation emits no release; reservation failure/expiry/release topics have no consumers found.
5. **Outbox not relayed.** Outbox records are stored, but publication uses a direct post-commit Kafka send. No retry/relay process or `published_at` updater was found; loss recovery is not established.
6. **Projection updates incomplete.** Fulfillment handler returns existing order record without applying newer delivery status.
7. **Fixed customer identity.** Notification, shipping, delivery, and fulfillment handlers assign hardcoded customer ID rather than propagating order/user identity.

## Frontend integration gaps

- Cart exists in local storage only, with one entry per pet and quantity one.
- Registration ignores returned auth token and navigates to login.
- Checkout uses editable customer ID; UUID-to-number mismatch persists.
- Catalog DTO/entity has no image property; pages use fallbacks (including remote Unsplash image URLs).
- No inventory/payment/notification/shipping/delivery/fulfillment integration, order history, tracking UI, admin UI, or profile editing.
- Category page links use query parameters; query parsing in the pets page should be verified before relying on them.
- Product name search is client-side over currently loaded page; no backend name query parameter exists.

## Known limitations and technical debt

- `PAYMENT_PENDING` enum state has no discovered transition.
- Inventory missing rows default to available in code; no catalog-to-inventory synchronization or verified seed data exists.
- Notifications are marked SENT without an actual send.
- No payment gateway, carrier, email/SMS/push provider, image upload/storage, shipping address/quotes, tax/shipping calculation, or external tracking.
- No many-item/quantity model; order SQL enforces quantity = 1.
- Separate database IDs are not joined across services by foreign keys; customer IDs are inconsistent.
- Single Kafka broker, one-partition/replica topic declarations, plaintext listeners; no production deployment config found.
- Compose has Postgres/Kafka health checks but no backend health checks. Frontend container runs Vite dev server.
- Timestamp SQL migrations mix `TIMESTAMPTZ` and `TIMESTAMP`; actual schema validation unverified.
- No frontend test/lint script; no user-service tests found.

## Planned features and recommendations

No formal roadmap or explicit planned backlog was found. Therefore, items below are **not committed plans**. They are functionality absent from the source or suggestions in the former context document, not project requirements:

- Production-grade payment processing/reconciliation and payment method flow.
- Complete event-driven checkout with explicit failure compensation and outbox retry semantics.
- Persistent cart and multi-quantity products.
- Customer identity linking/profile editing/order history and authorization across services.
- Admin catalog/role UI.
- External notification, carrier, and tracking integrations.
- Pet image management/storage and richer product categories.
- Frontend tests, end-to-end workflow tests, service readiness checks, and production security/deployment controls.

## Open questions and verification gaps

- Is the intended customer identifier numeric or the UUID user identity?
- Should order creation publish an event, or is a separate checkout/payment initiation step intended?
- What should happen on reservation failure/expiry, cancellation, and payment failure?
- Should payment use order total, and what are the currency/rounding requirements?
- Is an outbox relay/retry worker intended, or are outbox rows only retained for audit?
- Should later `DELIVERED` events update an existing fulfillment record?
- Are seed pets/customers expected for local startup?
- Do migrations and Hibernate mappings validate against PostgreSQL 16? Not run.
- Does the full Compose startup and frontend-to-backend flow work from a clean volume? Not run.
- What are intended production auth, CORS, TLS, broker security, secret management, health checks, and deployment model?
- Existing worktree modifications were present during the review. Their provenance/scope is unknown; inspect `git status` before changing or committing.

