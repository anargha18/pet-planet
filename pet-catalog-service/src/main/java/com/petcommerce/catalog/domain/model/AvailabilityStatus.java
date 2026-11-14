package com.petcommerce.catalog.domain.model;

/**
 * Read-only copy of inventory's availability (R-SAGA-05): changed only by inventory-status-events,
 * plus RETIRED when an admin archives the pet. It cannot be set through the API.
 */
public enum AvailabilityStatus {
    AVAILABLE,
    RESERVED,
    SOLD,
    /** Archived: hidden from the default catalog listing and never orderable. */
    RETIRED
}
