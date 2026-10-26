package com.petcommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.payment.entity.Payment;
import com.petcommerce.payment.entity.PaymentStatus;
import com.petcommerce.payment.event.PetReservedEvent;
import com.petcommerce.payment.repository.PaymentOutboxRepository;
import com.petcommerce.payment.repository.PaymentRepository;
import com.petcommerce.payment.repository.ProcessedEventRepository;
import com.petcommerce.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private Environment environment;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("payment.simulation-result", "SUCCESS")).thenReturn("SUCCESS");
        paymentService = new PaymentService(
                paymentRepository,
                processedEventRepository,
                paymentOutboxRepository,
                kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                environment
        );
    }

    @Test
    void processSuccessfulPaymentEvent() {
        UUID eventId = UUID.randomUUID();
        PetReservedEvent event = new PetReservedEvent(
                eventId,
                "PetReservedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                42L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(paymentRepository.findByOrderId(42L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            return payment;
        });

        Payment payment = paymentService.handlePetReservedEvent(event);

        assertNotNull(payment);
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(BigDecimal.valueOf(1000.00), payment.getAmount());
    }

    @Test
    void duplicateEventIsIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        PetReservedEvent event = new PetReservedEvent(
                eventId,
                "PetReservedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                101L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(true);
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.of(new Payment(101L, BigDecimal.valueOf(1000), "USD", PaymentStatus.SUCCEEDED, "txn-123")));

        Payment payment = paymentService.handlePetReservedEvent(event);

        assertNotNull(payment);
        assertEquals(101L, payment.getOrderId());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void failedSimulationCreatesFailedPayment() {
        when(environment.getProperty("payment.simulation-result", "SUCCESS")).thenReturn("FAILURE");
        paymentService = new PaymentService(
                paymentRepository,
                processedEventRepository,
                paymentOutboxRepository,
                kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                environment
        );

        UUID eventId = UUID.randomUUID();
        PetReservedEvent event = new PetReservedEvent(
                eventId,
                "PetReservedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                77L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(paymentRepository.findByOrderId(77L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.handlePetReservedEvent(event);

        assertNotNull(payment);
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentOutboxRepository, atLeastOnce()).save(any());
    }
}
