package com.petcommerce.payment.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.payment.domain.exception.PaymentNotFoundException;
import com.petcommerce.payment.domain.model.Payment;
import com.petcommerce.payment.persistence.repository.PaymentRepository;
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
class PaymentServiceOwnershipTest {

    private static final AuthenticatedCaller USER_7 = new AuthenticatedCaller(7L, "u7@example.com", false);
    private static final AuthenticatedCaller ADMIN = new AuthenticatedCaller(1L, "admin@example.com", true);

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService service;

    @Test
    void ownerReadsOwnOrderRecord() {
        var stored = record(7L);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(7L, service.getForOrder(10L, USER_7).customerId());
    }

    @Test
    void foreignOwnerIsRejected() {
        var stored = record(8L);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
    }

    @Test
    void adminReadsAnyOrderRecord() {
        var stored = record(8L);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertEquals(8L, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void recordWithUnknownOwnerIsAdminOnly() {
        var stored = record(null);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(stored));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
        assertEquals(null, service.getForOrder(10L, ADMIN).customerId());
    }

    @Test
    void missingRecordIsNotFound() {
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.getForOrder(10L, ADMIN));
    }

    private static Payment record(Long customerId) {
        Payment record = mock(Payment.class);
        when(record.getCustomerId()).thenReturn(customerId);
        return record;
    }
}
