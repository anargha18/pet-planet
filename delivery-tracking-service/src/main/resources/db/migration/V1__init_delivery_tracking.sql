CREATE TABLE delivery_records (
    id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    tracking_number VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE delivery_outbox (
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

CREATE INDEX idx_delivery_records_customer_id ON delivery_records(customer_id);
CREATE INDEX idx_delivery_records_status ON delivery_records(status);
CREATE INDEX idx_delivery_outbox_order_id ON delivery_outbox(order_id);
CREATE INDEX idx_delivery_outbox_created_at ON delivery_outbox(created_at);
