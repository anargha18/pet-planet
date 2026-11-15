package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.exception.TrackingNotFoundException;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ownership is enforced in the owning service (R-SEC-04). */
@ExtendWith(MockitoExtension.class)
class OrderTrackingQueryServiceTest {

    private static final AuthenticatedCaller USER_7 = new AuthenticatedCaller(7L, "u7@example.com", false);
    private static final AuthenticatedCaller ADMIN = new AuthenticatedCaller(1L, "admin@example.com", true);

    @Mock
    private OrderTrackingRepository trackingRepository;

    @Mock
    private OrderTrackingEventRepository timelineRepository;

    @InjectMocks
    private OrderTrackingQueryService service;

    @Test
    void ownerAndAdminReadTheOrderOthersDoNot() {
        OrderTracking tracking = new OrderTracking(10L);
        tracking.knowCustomer(7L);
        when(trackingRepository.findById(10L)).thenReturn(Optional.of(tracking));

        assertEquals(7L, service.getForOrder(10L, USER_7).summary().customerId());
        assertEquals(7L, service.getForOrder(10L, ADMIN).summary().customerId());
        assertThrows(AccessDeniedException.class,
                () -> service.getForOrder(10L, new AuthenticatedCaller(8L, null, false)));
    }

    @Test
    void unknownOwnerIsAdminOnly() {
        when(trackingRepository.findById(10L)).thenReturn(Optional.of(new OrderTracking(10L)));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
    }

    @Test
    void unseenOrderIsNotFound() {
        when(trackingRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(TrackingNotFoundException.class, () -> service.getForOrder(10L, ADMIN));
    }

    @Test
    void searchIsAdminOnly() {
        assertThrows(AccessDeniedException.class, () -> service.search(null, null, USER_7, PageRequest.of(0, 20)));
        verify(trackingRepository, never()).findAll(any(PageRequest.class));

        when(trackingRepository.findByEscalated(true, PageRequest.of(0, 20))).thenReturn(Page.empty());
        assertEquals(0, service.search(null, true, ADMIN, PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void mineUsesTheCallersId() {
        when(trackingRepository.findByCustomerId(7L, PageRequest.of(0, 20)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(new OrderTracking(10L))));

        assertEquals(1, service.getMine(USER_7, PageRequest.of(0, 20)).getTotalElements());
    }
}
