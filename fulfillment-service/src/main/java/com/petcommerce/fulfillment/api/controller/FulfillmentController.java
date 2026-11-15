package com.petcommerce.fulfillment.api.controller;

import com.petcommerce.fulfillment.api.dto.TrackingResponse;
import com.petcommerce.fulfillment.api.dto.TrackingSummaryResponse;
import com.petcommerce.fulfillment.application.OrderTrackingQueryService;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order-tracking read model API (DEC-2, R-SVC-01). Read-only; requires a valid JWT. */
@RestController
@RequestMapping("/api/v1/fulfillment/orders")
public class FulfillmentController {

    private final OrderTrackingQueryService service;

    public FulfillmentController(OrderTrackingQueryService service) {
        this.service = service;
    }

    /** One order with its timeline (owner or admin). */
    @GetMapping("/{orderId}")
    public TrackingResponse getOrder(AuthenticatedCaller caller, @PathVariable Long orderId) {
        return service.getForOrder(orderId, caller);
    }

    /** The caller's own orders, most recently updated first. */
    @GetMapping("/me")
    public Page<TrackingSummaryResponse> getMine(AuthenticatedCaller caller,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getMine(caller, pageable);
    }

    /** Admin only: every order, optional {@code customerId} and {@code escalated} filters. */
    @GetMapping
    public Page<TrackingSummaryResponse> search(AuthenticatedCaller caller,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean escalated,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(customerId, escalated, caller, pageable);
    }
}
