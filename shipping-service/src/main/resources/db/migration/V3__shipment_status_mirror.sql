-- Phase 4 (R-SVC-03): shipment statuses are CREATED -> SHIPPED -> DELIVERED / DELIVERY_FAILED,
-- mirrored from delivery-tracking events. PENDING/PACKAGED were never written by the code; mapped defensively.
UPDATE shipments SET status = 'CREATED' WHERE status IN ('PENDING', 'PACKAGED');
ALTER TABLE shipments
    ADD CONSTRAINT chk_shipments_status
    CHECK (status IN ('CREATED', 'SHIPPED', 'DELIVERED', 'DELIVERY_FAILED'));
