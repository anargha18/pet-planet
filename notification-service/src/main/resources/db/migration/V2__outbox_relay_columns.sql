-- Phase 1 (R-EVT-03): outbox rows are published by the platform outbox relay.
-- message_key: Kafka key chosen by the publisher; attempts/last_error: relay failure tracking.
ALTER TABLE notification_outbox ADD COLUMN message_key VARCHAR(255);
ALTER TABLE notification_outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notification_outbox ADD COLUMN last_error TEXT;
CREATE INDEX idx_notification_outbox_unpublished ON notification_outbox (created_at) WHERE published_at IS NULL;
