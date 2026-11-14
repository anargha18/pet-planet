package com.petcommerce.payment.application;

import com.petcommerce.payment.domain.model.Payment;
import com.petcommerce.payment.domain.model.PaymentStatus;
import com.petcommerce.payment.messaging.event.PaymentFailedEvent;
import com.petcommerce.payment.messaging.event.PaymentSucceededEvent;
import com.petcommerce.payment.messaging.event.PetReservedEvent;
import com.petcommerce.payment.messaging.publisher.PaymentEventPublisher;
import com.petcommerce.payment.persistence.repository.PaymentRepository;
import com.petcommerce.payment.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String PAYMENT_SUCCEEDED_TOPIC = "payment-succeeded-events";
    public static final String PAYMENT_FAILED_TOPIC = "payment-failed-events";

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentEventPublisher eventPublisher;
    private final String simulationResult;

    public PaymentService(
            PaymentRepository paymentRepository,
            ProcessedEventRepository processedEventRepository,
            PaymentEventPublisher eventPublisher,
            Environment environment) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
        this.simulationResult = environment.getProperty("payment.simulation-result", "SUCCESS").trim().toUpperCase();
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional
    public Payment handlePetReservedEvent(PetReservedEvent event) {
        if (event == null) {
            return null;
        }

        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping duplicate PetReservedEvent {}", event.eventId());
            return paymentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Payment existing = paymentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Payment already exists for order {} with payment {}", event.orderId(), existing.getPaymentId());
            return existing;
        }

        BigDecimal amount = (event.totalAmount() != null && event.totalAmount().compareTo(BigDecimal.ZERO) > 0)
                ? event.totalAmount()
                : BigDecimal.valueOf(1000.00);
        String currency = "USD";
        String transactionReference = "txn-" + UUID.randomUUID();
        Payment payment = new Payment(event.orderId(), amount, currency, PaymentStatus.PROCESSING, transactionReference);
        paymentRepository.save(payment);

        boolean success = "SUCCESS".equalsIgnoreCase(simulationResult);
        if (success) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setTransactionReference(transactionReference);
            payment = paymentRepository.save(payment);
            PaymentSucceededEvent successEvent = new PaymentSucceededEvent(
                    UUID.randomUUID(),
                    "PaymentSucceededEvent",
                    1,
                    event.aggregateId(),
                    Instant.now(),
                    event.correlationId(),
                    event.orderId(),
                    payment.getPaymentId(),
                    payment.getAmount()
            );
            log.info("Payment succeeded for order {} with payment {} amount {}", event.orderId(), payment.getPaymentId(), payment.getAmount());
            eventPublisher.publish(PAYMENT_SUCCEEDED_TOPIC, event.orderId().toString(), successEvent, payment.getPaymentId(), event.orderId());
            return payment;
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setTransactionReference("failed-" + UUID.randomUUID());
        payment = paymentRepository.save(payment);
        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                UUID.randomUUID(),
                "PaymentFailedEvent",
                1,
                event.aggregateId(),
                Instant.now(),
                event.correlationId(),
                event.orderId(),
                payment.getPaymentId(),
                payment.getAmount(),
                "PAYMENT_DECLINED"
        );
        log.warn("Payment failed for order {} with payment {}", event.orderId(), payment.getPaymentId());
        eventPublisher.publish(PAYMENT_FAILED_TOPIC, event.orderId().toString(), failedEvent, payment.getPaymentId(), event.orderId());
        return payment;
    }

}
