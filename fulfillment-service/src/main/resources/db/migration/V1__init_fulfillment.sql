CREATE TABLE fulfillment_records (
    id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    tracking_number VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL,
    notes TEXT NOT NULL
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE fulfillment_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    order_id BIGINT,
    correlation_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_fulfillment_records_customer_id ON fulfillment_records(customer_id);
CREATE INDEX idx_fulfillment_records_status ON fulfillment_records(status);
CREATE INDEX idx_fulfillment_outbox_order_id ON fulfillment_outbox(order_id);
CREATE INDEX idx_fulfillment_outbox_created_at ON fulfillment_outbox(created_at);
