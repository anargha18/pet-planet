package com.petcommerce.notification.domain.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationWordingTest {

    @Test
    void refundAmountReadsLikeAPrice() {
        assertEquals("Your payment of $1,250.00 for order #10 has been refunded.",
                NotificationWording.paymentRefunded(10L, new BigDecimal("1250")));
        assertEquals("Your payment for order #10 has been refunded.", NotificationWording.paymentRefunded(10L, null));
    }
}
