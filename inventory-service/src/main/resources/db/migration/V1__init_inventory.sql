CREATE TABLE IF NOT EXISTS pet_inventory (
    id UUID PRIMARY KEY,
    pet_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    order_id BIGINT,
    reservation_id UUID,
    reserved_at TIMESTAMPTZ,
    reservation_expires_at TIMESTAMPTZ,
    sold_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pet_inventory_order_id ON pet_inventory(order_id);
