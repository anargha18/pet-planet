package com.petcommerce.payment.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.payment.api.dto.PaymentResponse;
import com.petcommerce.payment.api.mapper.PaymentMapper;
import com.petcommerce.payment.domain.exception.PaymentNotFoundException;
import com.petcommerce.payment.domain.model.Payment;
import com.petcommerce.payment.domain.model.PaymentStatus;
import com.petcommerce.payment.messaging.event.PaymentFailedEvent;
import com.petcommerce.payment.messaging.event.PaymentRefundedEvent;
import com.petcommerce.payment.messaging.event.PaymentSucceededEvent;
import com.petcommerce.payment.messaging.event.PetReservedEvent;
import com.petcommerce.payment.messaging.publisher.PaymentEventPublisher;
import com.petcommerce.payment.persistence.repository.ClosedOrderRepository;
import com.petcommerce.payment.persistence.repository.PaymentRepository;
import com.petcommerce.payment.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Charges reserved orders and compensates closed ones (R-SAGA-04).
 * <ul>
 *   <li>PetReserved: charge the order total through the {@link PaymentGateway}, unless the order is already closed.</li>
 *   <li>OrderCancelled / OrderFailed: mark the order closed; refund a SUCCEEDED payment.</li>
 * </ul>
 * Both paths take a per-order database lock first, so a charge and a cancellation never interleave: whichever runs
 * second sees the other's result (closed order -> no charge; succeeded payment -> refund).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String PAYMENT_SUCCEEDED_TOPIC = "payment-succeeded-events";
    public static final String PAYMENT_FAILED_TOPIC = "payment-failed-events";
    public static final String PAYMENT_REFUNDED_TOPIC = "payment-refunded-events";
    private static final String CURRENCY = "USD";

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ClosedOrderRepository closedOrderRepository;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentGateway paymentGateway;

    public PaymentService(
            PaymentRepository paymentRepository,
            ProcessedEventRepository processedEventRepository,
            ClosedOrderRepository closedOrderRepository,
            PaymentEventPublisher eventPublisher,
            PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.closedOrderRepository = closedOrderRepository;
        this.eventPublisher = eventPublisher;
        this.paymentGateway = paymentGateway;
    }


    /** The payment of one order: 404 if none, 403 unless the caller owns the order or is an admin (R-SEC-04). */
    @Transactional(readOnly = true)
    public PaymentResponse getForOrder(Long orderId, AuthenticatedCaller caller) {
        Payment record = getPaymentByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId));
        caller.requireAccessTo(record.getCustomerId());
        return PaymentMapper.toResponse(record);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional
    public Payment handlePetReservedEvent(PetReservedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }
        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping duplicate PetReservedEvent {}", event.eventId());
            return paymentRepository.findByOrderId(event.orderId()).orElse(null);
        }
        if (event.totalAmount() == null || event.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            // Required field: fail so the record is retried and then dead-lettered (R-EVT-07); never charge a guess.
            throw new IllegalArgumentException("PetReservedEvent " + event.eventId() + " for order " + event.orderId()
                    + " has no positive totalAmount");
        }

        paymentRepository.lockOrder(event.orderId());
        if (closedOrderRepository.existsById(event.orderId())) {
            log.info("Order {} is already cancelled/failed: not charging", event.orderId());
            return null;
        }
        Payment existing = paymentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Payment already exists for order {} with payment {}", event.orderId(), existing.getPaymentId());
            return existing;
        }

        Payment payment = new Payment(event.orderId(), event.customerId(), event.totalAmount(), CURRENCY, PaymentStatus.PROCESSING, null);
        paymentRepository.save(payment);

        PaymentGateway.ChargeResult result = paymentGateway.charge(event.orderId(), event.totalAmount());
        payment.setTransactionReference(result.transactionReference());
        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment = paymentRepository.save(payment);
            log.info("Payment succeeded for order {} with payment {} amount {}", event.orderId(), payment.getPaymentId(), payment.getAmount());
            eventPublisher.publish(PAYMENT_SUCCEEDED_TOPIC, event.orderId().toString(),
                    new PaymentSucceededEvent(UUID.randomUUID(), "PaymentSucceededEvent", 1, event.aggregateId(), Instant.now(),
                            event.correlationId(), event.orderId(), payment.getPaymentId(), payment.getAmount(), payment.getCustomerId()),
                    payment.getPaymentId(), event.orderId());
            return payment;
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment = paymentRepository.save(payment);
        log.warn("Payment failed for order {} with payment {}: {}", event.orderId(), payment.getPaymentId(), result.failureReason());
        eventPublisher.publish(PAYMENT_FAILED_TOPIC, event.orderId().toString(),
                new PaymentFailedEvent(UUID.randomUUID(), "PaymentFailedEvent", 1, event.aggregateId(), Instant.now(),
                        event.correlationId(), event.orderId(), payment.getPaymentId(), payment.getAmount(), result.failureReason(),
                        payment.getCustomerId()),
                payment.getPaymentId(), event.orderId());
        return payment;
    }

    /**
     * The order was cancelled or failed. Remember it (so no later charge happens) and refund a successful charge.
     * Idempotent: a repeated event, or an already refunded payment, changes nothing.
     */
    @Transactional
    public void handleOrderClosed(UUID eventId, Long orderId, UUID correlationId, String reason) {
        if (orderId == null) {
            return;
        }
        if (eventId != null && processedEventRepository.markProcessed(eventId) == 0) {
            log.info("Skipping duplicate order-closed event {}", eventId);
            return;
        }
        paymentRepository.lockOrder(orderId);
        closedOrderRepository.markClosed(orderId, truncate(reason));

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            log.info("Order {} closed ({}); nothing to refund (payment {})", orderId, reason,
                    payment == null ? "none" : payment.getStatus());
            return;
        }
        String refundReference = paymentGateway.refund(orderId, payment.getAmount(), payment.getTransactionReference());
        payment.refund(truncate(reason), refundReference);
        paymentRepository.save(payment);
        log.info("Refunded payment {} of {} for closed order {} ({})", payment.getPaymentId(), payment.getAmount(), orderId, reason);
        eventPublisher.publish(PAYMENT_REFUNDED_TOPIC, orderId.toString(),
                new PaymentRefundedEvent(UUID.randomUUID(), "PaymentRefundedEvent", 1, UUID.randomUUID(), Instant.now(),
                        correlationId, orderId, payment.getPaymentId(), payment.getAmount(), reason, payment.getCustomerId()),
                payment.getPaymentId(), orderId);
    }

    private static String truncate(String reason) {
        String value = reason == null ? "UNKNOWN" : reason;
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
