-- Phase 4 (R-SAGA-09): notifications are typed, at most one per order and type.
-- Every notification written before this migration was the order-confirmation message.
ALTER TABLE notifications ADD COLUMN type VARCHAR(32);
UPDATE notifications SET type = 'ORDER_CONFIRMED';
ALTER TABLE notifications ALTER COLUMN type SET NOT NULL;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
    CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED', 'ORDER_FAILED', 'PAYMENT_REFUNDED',
                    'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED'));
CREATE UNIQUE INDEX uq_notifications_order_type ON notifications (order_id, type);
