CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    pet_id UUID,
    order_id BIGINT,
    correlation_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_inventory_outbox_created_at ON inventory_outbox(created_at);
