-- Phase 2 (R-SAGA-01): PAID is removed from the order lifecycle. An order becomes CONFIRMED once payment succeeded
-- AND inventory marked every pet SOLD; existing PAID orders had both, so they are CONFIRMED.
UPDATE orders SET status = 'CONFIRMED', updated_at = NOW() WHERE status = 'PAID';
