package com.petcommerce.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.payment.domain.model.Payment;
import com.petcommerce.payment.domain.model.PaymentStatus;
import com.petcommerce.payment.messaging.event.PetReservedEvent;
import com.petcommerce.payment.messaging.publisher.PaymentEventPublisher;
import com.petcommerce.payment.persistence.entity.PaymentOutboxEvent;
import com.petcommerce.payment.persistence.repository.ClosedOrderRepository;
import com.petcommerce.payment.persistence.repository.PaymentOutboxRepository;
import com.petcommerce.payment.persistence.repository.PaymentRepository;
import com.petcommerce.payment.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long ORDER_ID = 42L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ClosedOrderRepository closedOrderRepository;

    @Mock
    private PaymentOutboxRepository paymentOutboxRepository;

    private final StubGateway gateway = new StubGateway();

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Default: every event is seen for the first time; duplicate-handling tests override this.
        lenient().when(processedEventRepository.markProcessed(any())).thenReturn(1);
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        paymentService = new PaymentService(paymentRepository, processedEventRepository, closedOrderRepository,
                new PaymentEventPublisher(paymentOutboxRepository, new ObjectMapper().findAndRegisterModules()), gateway);
    }

    @Test
    void reservedOrderIsChargedItsTotalAndSuccessIsPublished() {
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        Payment payment = paymentService.handlePetReservedEvent(reserved(new BigDecimal("950.00")));

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(new BigDecimal("950.00"), payment.getAmount());
        verify(paymentRepository).lockOrder(ORDER_ID);
        assertEquals(List.of("PaymentSucceededEvent"), publishedEventTypes());
    }

    @Test
    void declinedChargeCreatesFailedPaymentAndPublishesFailure() {
        gateway.succeed = false;
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        Payment payment = paymentService.handlePetReservedEvent(reserved(BigDecimal.TEN));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(List.of("PaymentFailedEvent"), publishedEventTypes());
    }

    @Test
    void duplicatePetReservedEventDoesNotChargeTwice() {
        PetReservedEvent event = reserved(BigDecimal.TEN);
        when(processedEventRepository.markProcessed(event.eventId())).thenReturn(0);

        paymentService.handlePetReservedEvent(event);

        assertEquals(0, gateway.charges);
        verify(paymentOutboxRepository, never()).save(any());
    }

    @Test
    void orderClosedBeforeChargeIsNeverCharged() {
        when(closedOrderRepository.existsById(ORDER_ID)).thenReturn(true);

        Payment payment = paymentService.handlePetReservedEvent(reserved(BigDecimal.TEN));

        assertNull(payment);
        assertEquals(0, gateway.charges);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void missingTotalAmountIsRejectedInsteadOfChargingAGuess() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.handlePetReservedEvent(reserved(null)));
        assertEquals(0, gateway.charges);
    }

    @Test
    void closingAnOrderRefundsItsSuccessfulPayment() {
        Payment succeeded = new Payment(ORDER_ID, 7L, new BigDecimal("950.00"), "USD", PaymentStatus.SUCCEEDED, "txn-1");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(succeeded));

        paymentService.handleOrderClosed(UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), "ORDER_CANCELLED:CUSTOMER_CANCELLED");

        assertEquals(PaymentStatus.REFUNDED, succeeded.getStatus());
        assertNotNull(succeeded.getRefundedAt());
        assertEquals("ORDER_CANCELLED:CUSTOMER_CANCELLED", succeeded.getRefundReason());
        verify(closedOrderRepository).markClosed(ORDER_ID, "ORDER_CANCELLED:CUSTOMER_CANCELLED");
        assertEquals(List.of("PaymentRefundedEvent"), publishedEventTypes());
    }

    @Test
    void closingAnOrderWithoutSuccessfulPaymentOnlyRecordsTheClosure() {
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        paymentService.handleOrderClosed(UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), "ORDER_FAILED:RESERVATION_FAILED");

        verify(closedOrderRepository).markClosed(anyLong(), anyString());
        verify(paymentOutboxRepository, never()).save(any());
    }

    private PetReservedEvent reserved(BigDecimal total) {
        return new PetReservedEvent(UUID.randomUUID(), "PetReservedEvent", 1, UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), UUID.randomUUID(), total, 7L);
    }

    private List<String> publishedEventTypes() {
        ArgumentCaptor<PaymentOutboxEvent> captor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(paymentOutboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(PaymentOutboxEvent::getEventType).toList();
    }

    private static final class StubGateway implements PaymentGateway {
        boolean succeed = true;
        int charges;

        @Override
        public ChargeResult charge(Long orderId, BigDecimal amount) {
            charges++;
            return succeed ? new ChargeResult(true, "txn-test", null) : new ChargeResult(false, "failed-test", "PAYMENT_DECLINED");
        }

        @Override
        public String refund(Long orderId, BigDecimal amount, String transactionReference) {
            return "refund-test";
        }
    }
}
