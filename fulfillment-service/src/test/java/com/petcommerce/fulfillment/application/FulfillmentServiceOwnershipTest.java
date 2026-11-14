package com.petcommerce.fulfillment.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.fulfillment.domain.exception.FulfillmentNotFoundException;
import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ownership is enforced here, in the owning service (R-SEC-04), not at the gateway. */
@ExtendWith(MockitoExtension.class)
class FulfillmentServiceOwnershipTest {

    private static final AuthenticatedCaller USER_7 = new AuthenticatedCaller(7L, "u7@example.com", false);
    private static final AuthenticatedCaller ADMIN = new AuthenticatedCaller(1L, "admin@example.com", true);

    @Mock
    private FulfillmentRecordRepository fulfillmentRecordRepository;

    @InjectMocks
    private FulfillmentService service;

    @Test
    void ownerReadsOwnOrderRecord() {
        var stored = record(7L);
        when(fulfillmentRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(7L, service.getForOrder(10L, USER_7).customerId());
    }

    @Test
    void foreignOwnerIsRejected() {
        var stored = record(8L);
        when(fulfillmentRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
    }

    @Test
    void adminReadsAnyOrderRecord() {
        var stored = record(8L);
        when(fulfillmentRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(8L, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void recordWithUnknownOwnerIsAdminOnly() {
        var stored = record(null);
        when(fulfillmentRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
        assertEquals(null, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void missingRecordIsNotFound() {
        when(fulfillmentRecordRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThrows(FulfillmentNotFoundException.class, () -> service.getForOrder(10L, ADMIN));
    }

    @Test
    void userListsOnlyOwnRecords() {
        var stored = record(7L);
        when(fulfillmentRecordRepository.findByCustomerId(7L)).thenReturn(List.of(stored));

        assertEquals(1, service.getForCustomer(7L, USER_7).size());
        assertThrows(AccessDeniedException.class, () -> service.getForCustomer(8L, USER_7));
        verify(fulfillmentRecordRepository, never()).findByCustomerId(8L);
    }

    @Test
    void adminListsAnyCustomer() {
        var stored = record(8L);
        when(fulfillmentRecordRepository.findByCustomerId(8L)).thenReturn(List.of(stored));

        assertEquals(1, service.getForCustomer(8L, ADMIN).size());
    }

    private static FulfillmentRecord record(Long customerId) {
        FulfillmentRecord record = mock(FulfillmentRecord.class);
        when(record.getCustomerId()).thenReturn(customerId);
        return record;
    }
}
