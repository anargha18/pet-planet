package com.petcommerce.order.api.mapper;

import com.petcommerce.order.api.dto.OrderItemResponse;
import com.petcommerce.order.api.dto.OrderResponse;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderItem;

import java.util.List;

/** Converts order entities to API response records. Call inside a transaction (items are lazy). */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderMapper::toResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getPetId(),
                item.getPetName(),
                item.getPetSpecies(),
                item.getPetBreed(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
