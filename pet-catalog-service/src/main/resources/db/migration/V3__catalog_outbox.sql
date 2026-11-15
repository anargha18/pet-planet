-- Phase 1 (R-EVT-03, R-SAGA-05 part): catalog events go through a transactional outbox instead of a direct
-- Kafka send inside the database transaction. Published by the platform outbox relay.
CREATE TABLE catalog_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    pet_id UUID,
    correlation_id UUID,
    message_key VARCHAR(255),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_catalog_outbox_unpublished ON catalog_outbox (created_at) WHERE published_at IS NULL;
