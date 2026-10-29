# Fulfillment service

**Module:** `fulfillment-service` · **Package root:** `com.petcommerce.fulfillment` · **Database:** `petplanet_fulfillment` · **Port:** 8087.

- Consumes `delivery-status-updated-events`, creates a `FulfillmentRecord` (`SHIPPED` for `IN_TRANSIT`, `DELIVERED` for `DELIVERED`), and updates the existing record when subsequent delivery status changes arrive.
