package com.petcommerce.payment.api.mapper;

import com.petcommerce.payment.api.dto.PaymentResponse;
import com.petcommerce.payment.domain.model.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(),
                p.getOrderId(),
                p.getCustomerId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getTransactionReference(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getRefundedAt(),
                p.getRefundReason()
        );
    }
}
