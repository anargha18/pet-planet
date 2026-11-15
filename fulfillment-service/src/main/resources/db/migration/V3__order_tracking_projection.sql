-- Phase 4 (DEC-2, R-SVC-01): fulfillment-service becomes the order-tracking read model.
-- The old per-delivery record, its outbox (topic fulfillment-updated-events is retired) and processed_events
-- (idempotency now comes from order_tracking_events.event_id) are replaced. The new projection is filled by
-- replaying the Kafka topics with a new consumer group, so no data is copied.
DROP TABLE fulfillment_outbox;
DROP TABLE fulfillment_records;
DROP TABLE processed_events;

CREATE TABLE order_tracking (
    order_id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    order_status VARCHAR(32),
    order_status_at TIMESTAMPTZ,
    total_amount NUMERIC(12, 2),
    payment_status VARCHAR(32),
    shipment_status VARCHAR(32),
    tracking_number VARCHAR(128),
    delivery_status VARCHAR(32),
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    escalation_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_tracking_customer ON order_tracking (customer_id, updated_at DESC);
CREATE INDEX idx_order_tracking_open ON order_tracking (order_status, order_status_at) WHERE escalated = FALSE;

CREATE TABLE order_tracking_events (
    event_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_tracking_events_order ON order_tracking_events (order_id, occurred_at);
