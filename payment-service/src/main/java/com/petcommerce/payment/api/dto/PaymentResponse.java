package com.petcommerce.payment.api.dto;

import com.petcommerce.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** HTTP view of a payment (R-SEC-06: controllers never return JPA entities). */
public record PaymentResponse(
        UUID paymentId,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String transactionReference,
        Instant createdAt,
        Instant updatedAt,
        Instant refundedAt,
        String refundReason
) {
}
