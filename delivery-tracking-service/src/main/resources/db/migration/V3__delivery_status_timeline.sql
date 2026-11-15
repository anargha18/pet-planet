-- Phase 4 (R-SAGA-06, R-SVC-02): delivery status set IN_TRANSIT/OUT_FOR_DELIVERY/DELIVERED/DELIVERY_FAILED,
-- the saga correlation ID kept per delivery, and a timeline table (one row per status reached).
UPDATE delivery_records SET status = 'DELIVERY_FAILED' WHERE status = 'FAILED';
ALTER TABLE delivery_records
    ADD CONSTRAINT chk_delivery_records_status
    CHECK (status IN ('IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED'));
ALTER TABLE delivery_records ADD COLUMN correlation_id UUID;

CREATE TABLE delivery_events (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery_records (id),
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_delivery_events_order_id ON delivery_events (order_id, occurred_at);

-- Timeline for deliveries that existed before this migration.
INSERT INTO delivery_events (id, delivery_id, order_id, status, note, occurred_at)
SELECT gen_random_uuid(), id, order_id, 'IN_TRANSIT', 'Recorded before the delivery timeline existed', created_at
FROM delivery_records;
INSERT INTO delivery_events (id, delivery_id, order_id, status, note, occurred_at)
SELECT gen_random_uuid(), id, order_id, status, 'Recorded before the delivery timeline existed',
       COALESCE(delivered_at, updated_at)
FROM delivery_records
WHERE status <> 'IN_TRANSIT';
