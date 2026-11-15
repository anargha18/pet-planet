package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.model.EscalationReason;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.TrackedOrderStatus;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Flags orders that have stayed CREATED/PAYMENT_PENDING longer than {@code fulfillment.escalation.stuck-after}
 * (R-SVC-01). The flag is cleared automatically if the order later reaches a terminal status. Delivery failures are
 * escalated directly by the projector.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final OrderTrackingRepository trackingRepository;
    private final OrderTrackingEventRepository timelineRepository;
    private final Duration stuckAfter;
    private final Clock clock;

    @Autowired
    public EscalationService(OrderTrackingRepository trackingRepository,
                             OrderTrackingEventRepository timelineRepository,
                             @Value("${fulfillment.escalation.stuck-after:PT10M}") Duration stuckAfter) {
        this(trackingRepository, timelineRepository, stuckAfter, Clock.systemUTC());
    }

    EscalationService(OrderTrackingRepository trackingRepository, OrderTrackingEventRepository timelineRepository,
                      Duration stuckAfter, Clock clock) {
        this.trackingRepository = trackingRepository;
        this.timelineRepository = timelineRepository;
        this.stuckAfter = stuckAfter;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${fulfillment.escalation.check-interval-ms:60000}",
            fixedDelayString = "${fulfillment.escalation.check-interval-ms:60000}")
    @Transactional
    public int escalateStuckOrders() {
        Instant now = clock.instant();
        List<OrderTracking> stuck = trackingRepository.findByEscalatedFalseAndOrderStatusInAndOrderStatusAtBefore(
                EnumSet.of(TrackedOrderStatus.CREATED, TrackedOrderStatus.PAYMENT_PENDING), now.minus(stuckAfter));
        for (OrderTracking tracking : stuck) {
            tracking.escalate(EscalationReason.STUCK_BEFORE_CONFIRMATION);
            tracking.touch(now);
            trackingRepository.save(tracking);
            timelineRepository.insertIfAbsent(UUID.randomUUID(), tracking.getOrderId(), "Escalated", "fulfillment-service",
                    "Escalated: order still " + tracking.getOrderStatus() + " after " + stuckAfter, now);
            log.warn("Escalated order {}: still {} since {}", tracking.getOrderId(), tracking.getOrderStatus(),
                    tracking.getOrderStatusAt());
        }
        return stuck.size();
    }
}
