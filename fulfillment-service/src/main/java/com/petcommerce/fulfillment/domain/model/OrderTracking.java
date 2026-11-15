package com.petcommerce.fulfillment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order's denormalized tracking view (DEC-2, R-SVC-01), built only from events. Every {@code apply…} method is
 * monotonic, so events from different topics may arrive in any order and replays do not move a status backwards.
 * This service never writes another service's data; the view can be rebuilt by replaying the topics.
 */
@Entity
@Table(name = "order_tracking")
public class OrderTracking {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private TrackedOrderStatus orderStatus;

    @Column(name = "order_status_at")
    private Instant orderStatusAt;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private TrackedPaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status")
    private TrackedShipmentStatus shipmentStatus;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private TrackedDeliveryStatus deliveryStatus;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_reason")
    private EscalationReason escalationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderTracking() {
    }

    /** For tests; at runtime rows are created by the repository's insert-if-absent. */
    public OrderTracking(Long orderId) {
        this.orderId = orderId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void touch(Instant at) {
        updatedAt = at;
    }

    public void knowCustomer(Long id) {
        if (customerId == null && id != null) {
            customerId = id;
        }
    }

    public void knowTotal(BigDecimal amount) {
        if (totalAmount == null && amount != null) {
            totalAmount = amount;
        }
    }

    public void applyOrderStatus(TrackedOrderStatus next, Instant at) {
        if (orderStatus == null || orderStatus.canBeReplacedBy(next)) {
            orderStatus = next;
            orderStatusAt = at;
            if (next.isTerminal() && escalationReason == EscalationReason.STUCK_BEFORE_CONFIRMATION) {
                escalated = false;
                escalationReason = null;
            }
        }
    }

    public void applyPaymentStatus(TrackedPaymentStatus next) {
        if (next == TrackedPaymentStatus.REFUNDED) {
            paymentStatus = next;
        } else if (paymentStatus == null
                || (paymentStatus == TrackedPaymentStatus.PENDING && next != TrackedPaymentStatus.PENDING)) {
            paymentStatus = next;
        }
    }

    public void applyShipment(String tracking) {
        if (tracking != null) {
            trackingNumber = tracking;
        }
        if (shipmentStatus == null) {
            shipmentStatus = TrackedShipmentStatus.CREATED;
        }
    }

    public void applyDelivery(TrackedDeliveryStatus next, String tracking) {
        if (next == null) {
            return;
        }
        if (trackingNumber == null) {
            trackingNumber = tracking;
        }
        if (deliveryStatus == null || (deliveryStatus.rank() < 2 && next.rank() > deliveryStatus.rank())) {
            deliveryStatus = next;
        }
        TrackedShipmentStatus mirrored = next.shipmentStatus();
        if (shipmentStatus == null || (shipmentStatus.rank() < 2 && mirrored.rank() > shipmentStatus.rank())) {
            shipmentStatus = mirrored;
        }
        if (next == TrackedDeliveryStatus.DELIVERY_FAILED) {
            escalate(EscalationReason.DELIVERY_FAILED);
        }
    }

    /** Returns false if already escalated (nothing changed). */
    public boolean escalate(EscalationReason reason) {
        if (escalated && escalationReason == reason) {
            return false;
        }
        escalated = true;
        escalationReason = reason;
        return true;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public TrackedOrderStatus getOrderStatus() {
        return orderStatus;
    }

    public Instant getOrderStatusAt() {
        return orderStatusAt;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public TrackedPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public TrackedShipmentStatus getShipmentStatus() {
        return shipmentStatus;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public TrackedDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public EscalationReason getEscalationReason() {
        return escalationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
