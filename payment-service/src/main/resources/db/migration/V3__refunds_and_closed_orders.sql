-- Phase 2 (R-SAGA-04): refunds and the closed-order marker.
-- A SUCCEEDED payment for an order that is later cancelled/failed becomes REFUNDED.
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN refund_reason VARCHAR(64);

-- Orders known to be cancelled/failed: a later PetReserved for them must never charge.
CREATE TABLE closed_orders (
    order_id BIGINT PRIMARY KEY,
    reason VARCHAR(64) NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL
);
