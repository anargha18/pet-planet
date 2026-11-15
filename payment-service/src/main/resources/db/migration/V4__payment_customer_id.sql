-- Phase 3 (R-SEC-04): payment enforces ownership itself, so it records the order's owner.
-- Filled from the customerId that inventory now adds to PetReservedEvent. Payments created before this
-- migration keep NULL and are visible to admins only.
ALTER TABLE payments ADD COLUMN customer_id BIGINT;
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
