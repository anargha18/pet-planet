package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.api.dto.TrackingResponse;
import com.petcommerce.fulfillment.api.dto.TrackingSummaryResponse;
import com.petcommerce.fulfillment.api.mapper.TrackingMapper;
import com.petcommerce.fulfillment.domain.exception.TrackingNotFoundException;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingEventRepository;
import com.petcommerce.fulfillment.persistence.repository.OrderTrackingRepository;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads of the tracking projection with ownership checks (R-SEC-04). */
@Service
public class OrderTrackingQueryService {

    private final OrderTrackingRepository trackingRepository;
    private final OrderTrackingEventRepository timelineRepository;

    public OrderTrackingQueryService(OrderTrackingRepository trackingRepository, OrderTrackingEventRepository timelineRepository) {
        this.trackingRepository = trackingRepository;
        this.timelineRepository = timelineRepository;
    }

    /** 404 if the projection has not seen the order yet; 403 unless owner or admin (unknown owner: admin only). */
    @Transactional(readOnly = true)
    public TrackingResponse getForOrder(Long orderId, AuthenticatedCaller caller) {
        OrderTracking tracking = trackingRepository.findById(orderId).orElseThrow(() -> new TrackingNotFoundException(orderId));
        caller.requireAccessTo(tracking.getCustomerId());
        return TrackingMapper.toResponse(tracking, timelineRepository.findByOrderIdOrderByOccurredAtAscRecordedAtAsc(orderId));
    }

    @Transactional(readOnly = true)
    public Page<TrackingSummaryResponse> getMine(AuthenticatedCaller caller, Pageable pageable) {
        return trackingRepository.findByCustomerId(caller.userId(), pageable).map(TrackingMapper::toSummary);
    }

    /** Admin only: all orders, optionally filtered by customer and/or escalation flag. */
    @Transactional(readOnly = true)
    public Page<TrackingSummaryResponse> search(Long customerId, Boolean escalated, AuthenticatedCaller caller, Pageable pageable) {
        caller.requireAdmin();
        Page<OrderTracking> page;
        if (customerId != null && escalated != null) {
            page = trackingRepository.findByCustomerIdAndEscalated(customerId, escalated, pageable);
        } else if (customerId != null) {
            page = trackingRepository.findByCustomerId(customerId, pageable);
        } else if (escalated != null) {
            page = trackingRepository.findByEscalated(escalated, pageable);
        } else {
            page = trackingRepository.findAll(pageable);
        }
        return page.map(TrackingMapper::toSummary);
    }
}
