package com.petcommerce.notification.application;

import com.petcommerce.notification.domain.model.Notification;
import com.petcommerce.notification.domain.model.NotificationStatus;
import com.petcommerce.notification.domain.model.NotificationType;
import com.petcommerce.notification.persistence.repository.NotificationRepository;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ownership is enforced here, in the owning service (R-SEC-04), not at the gateway. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceOwnershipTest {

    private static final AuthenticatedCaller USER_7 = new AuthenticatedCaller(7L, "u7@example.com", false);
    private static final AuthenticatedCaller ADMIN = new AuthenticatedCaller(1L, "admin@example.com", true);

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService service;

    @Test
    void ownerReadsAllNotificationsOfOwnOrder() {
        when(notificationRepository.findByOrderIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(notification(7L, NotificationType.ORDER_CONFIRMED), notification(7L, NotificationType.SHIPPED)));

        assertEquals(2, service.getForOrder(10L, USER_7).size());
    }

    @Test
    void foreignOrderIsRejected() {
        when(notificationRepository.findByOrderIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(notification(8L, NotificationType.ORDER_CONFIRMED)));

        assertThrows(AccessDeniedException.class, () -> service.getForOrder(10L, USER_7));
    }

    @Test
    void adminReadsAnyOrder() {
        when(notificationRepository.findByOrderIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(notification(8L, NotificationType.ORDER_CONFIRMED)));

        assertEquals(8L, service.getForOrder(10L, ADMIN).get(0).customerId());
    }

    @Test
    void orderWithoutNotificationsYetIsAnEmptyList() {
        when(notificationRepository.findByOrderIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        assertTrue(service.getForOrder(10L, USER_7).isEmpty());
    }

    @Test
    void userListsOnlyOwnCustomerNotifications() {
        when(notificationRepository.findByCustomerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(notification(7L, NotificationType.ORDER_CONFIRMED)));

        assertEquals(1, service.getForCustomer(7L, USER_7).size());
        assertThrows(AccessDeniedException.class, () -> service.getForCustomer(8L, USER_7));
        verify(notificationRepository, never()).findByCustomerIdOrderByCreatedAtDesc(8L);
    }

    private static Notification notification(Long customerId, NotificationType type) {
        return new Notification(10L, customerId, type, "m", NotificationStatus.SENT);
    }
}
