package com.petcommerce.order.controller;

import com.petcommerce.order.dto.CreateOrderRequest;
import com.petcommerce.order.dto.OrderResponse;
import com.petcommerce.order.entity.OrderStatus;
import com.petcommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders", description = "Order operations")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Pet not found"),
            @ApiResponse(responseCode = "409", description = "Pet unavailable"),
            @ApiResponse(responseCode = "503", description = "Pet Catalog unavailable")
    })
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order by ID")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping("/orders")
    @Operation(summary = "List customer orders")
    public Page<OrderResponse> listOrders(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        validateSort(pageable);
        return orderService.getOrders(customerId, status, pageable);
    }

    private void validateSort(Pageable pageable) {
        Set<String> allowedFields = Set.of("id", "customerId", "status", "createdAt", "updatedAt");
        pageable.getSort().forEach(order -> {
            if (!allowedFields.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
            }
        });
    }

    @PatchMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public OrderResponse cancelOrder(@PathVariable Long orderId) {
        return orderService.cancel(orderId);
    }
}
