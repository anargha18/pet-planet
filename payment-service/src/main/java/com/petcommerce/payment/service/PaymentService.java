package com.petcommerce.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.payment.entity.Payment;
import com.petcommerce.payment.entity.PaymentOutboxEvent;
import com.petcommerce.payment.entity.PaymentStatus;
import com.petcommerce.payment.entity.ProcessedEvent;
import com.petcommerce.payment.event.PaymentFailedEvent;
import com.petcommerce.payment.event.PaymentSucceededEvent;
import com.petcommerce.payment.event.PetReservedEvent;
import com.petcommerce.payment.repository.PaymentOutboxRepository;
import com.petcommerce.payment.repository.PaymentRepository;
import com.petcommerce.payment.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String PAYMENT_SUCCEEDED_TOPIC = "payment-succeeded-events";
    public static final String PAYMENT_FAILED_TOPIC = "payment-failed-events";

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String simulationResult;

    public PaymentService(
            PaymentRepository paymentRepository,
            ProcessedEventRepository processedEventRepository,
            PaymentOutboxRepository paymentOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            Environment environment) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.paymentOutboxRepository = paymentOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
        this.simulationResult = environment.getProperty("payment.simulation-result", "SUCCESS").trim().toUpperCase();
    }

    @Transactional
    public Payment handlePetReservedEvent(PetReservedEvent event) {
        if (event == null) {
            return null;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping duplicate PetReservedEvent {}", event.eventId());
            return paymentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("PetReservedEvent {} already processed; ignoring safely", event.eventId());
            return paymentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Payment existing = paymentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Payment already exists for order {} with payment {}", event.orderId(), existing.getPaymentId());
            return existing;
        }

        BigDecimal amount = BigDecimal.valueOf(1000.00);
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
            log.info("Payment succeeded for order {} with payment {}", event.orderId(), payment.getPaymentId());
            publishAfterCommit(PAYMENT_SUCCEEDED_TOPIC, event.orderId().toString(), successEvent, payment.getPaymentId(), event.orderId());
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
        publishAfterCommit(PAYMENT_FAILED_TOPIC, event.orderId().toString(), failedEvent, payment.getPaymentId(), event.orderId());
        return payment;
    }

    private void publishAfterCommit(String topic, String key, Object event, UUID paymentId, Long orderId) {
        paymentOutboxRepository.save(new PaymentOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                paymentId,
                serializePayload(event),
                Instant.now()
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, event);
                }
            });
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment event", ex);
        }
    }
}
