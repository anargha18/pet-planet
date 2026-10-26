CREATE TABLE IF NOT EXISTS pet_inventory (
    id UUID PRIMARY KEY,
    pet_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    reservation_id UUID,
    reserved_at TIMESTAMP,
    reservation_expires_at TIMESTAMP,
    sold_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
