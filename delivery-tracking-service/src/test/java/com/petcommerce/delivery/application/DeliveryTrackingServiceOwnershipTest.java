package com.petcommerce.delivery.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.delivery.domain.exception.DeliveryNotFoundException;
import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
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
class DeliveryTrackingServiceOwnershipTest {

    private static final AuthenticatedCaller USER_7 = new AuthenticatedCaller(7L, "u7@example.com", false);
    private static final AuthenticatedCaller ADMIN = new AuthenticatedCaller(1L, "admin@example.com", true);

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @InjectMocks
    private DeliveryTrackingService service;

    @Test
    void ownerReadsOwnOrderRecord() {
        var stored = record(7L);
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(7L, service.getForOrder(10L, USER_7).customerId());
    }

    @Test
    void foreignOwnerIsRejected() {
        var stored = record(8L);
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
    }

    @Test
    void adminReadsAnyOrderRecord() {
        var stored = record(8L);
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(8L, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void recordWithUnknownOwnerIsAdminOnly() {
        var stored = record(null);
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
        assertEquals(null, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void missingRecordIsNotFound() {
        when(deliveryRecordRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThrows(DeliveryNotFoundException.class, () -> service.getForOrder(10L, ADMIN));
    }

    @Test
    void userListsOnlyOwnRecords() {
        var stored = record(7L);
        when(deliveryRecordRepository.findByCustomerId(7L)).thenReturn(List.of(stored));

        assertEquals(1, service.getForCustomer(7L, USER_7).size());
        assertThrows(AccessDeniedException.class, () -> service.getForCustomer(8L, USER_7));
        verify(deliveryRecordRepository, never()).findByCustomerId(8L);
    }

    @Test
    void adminListsAnyCustomer() {
        var stored = record(8L);
        when(deliveryRecordRepository.findByCustomerId(8L)).thenReturn(List.of(stored));

        assertEquals(1, service.getForCustomer(8L, ADMIN).size());
    }

    private static DeliveryRecord record(Long customerId) {
        DeliveryRecord record = mock(DeliveryRecord.class);
        when(record.getCustomerId()).thenReturn(customerId);
        return record;
    }
}
