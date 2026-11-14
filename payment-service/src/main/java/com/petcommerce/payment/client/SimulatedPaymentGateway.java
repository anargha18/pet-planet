package com.petcommerce.payment.client;

import com.petcommerce.payment.application.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Simulated card processor (DEC-4). No real money moves.
 * <ul>
 *   <li>{@code payment.simulation-result}: SUCCESS (default) or FAILURE.</li>
 *   <li>{@code payment.simulation-delay}: how long a charge "takes" (ISO-8601, default PT0S). The smoke test uses a
 *       delay to reproduce cancellations and reservation expiry while a charge is in progress.</li>
 * </ul>
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final boolean succeed;
    private final Duration delay;

    public SimulatedPaymentGateway(
            @Value("${payment.simulation-result:SUCCESS}") String simulationResult,
            @Value("${payment.simulation-delay:PT0S}") Duration delay) {
        this.succeed = "SUCCESS".equalsIgnoreCase(simulationResult.trim());
        this.delay = delay;
    }

    @Override
    public ChargeResult charge(Long orderId, BigDecimal amount) {
        if (!delay.isZero()) {
            log.info("Simulating a slow charge for order {}: {}", orderId, delay);
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new ChargeResult(false, "failed-" + UUID.randomUUID(), "INTERRUPTED");
            }
        }
        return succeed
                ? new ChargeResult(true, "txn-" + UUID.randomUUID(), null)
                : new ChargeResult(false, "failed-" + UUID.randomUUID(), "PAYMENT_DECLINED");
    }

    @Override
    public String refund(Long orderId, BigDecimal amount, String transactionReference) {
        return "refund-" + UUID.randomUUID();
    }
}
