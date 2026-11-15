package com.petcommerce.inventory.domain.model;

/** Availability of one unique pet. Inventory is the source of truth; the catalog keeps a read-only copy. */
public enum InventoryStatus {
    AVAILABLE,
    RESERVED,
    SOLD,
    /** The pet was archived in the catalog and can no longer be reserved. */
    RETIRED
}
