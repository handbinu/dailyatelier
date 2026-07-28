package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;

import java.util.List;

final class OrderViewPolicy {
    private OrderViewPolicy() {
    }

    static String orderNumber(Long orderId) {
        return "ORD-%010d".formatted(orderId);
    }

    static List<OrderAction> buyerActions(Order order) {
        return switch (order.getStatus()) {
            case PAYMENT_PENDING -> List.of(
                    OrderAction.UPDATE_SHIPPING_ADDRESS,
                    OrderAction.CANCEL
            );
            case DELIVERED -> List.of(OrderAction.CONFIRM);
            default -> List.of();
        };
    }

    static List<OrderAction> sellerActions(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            return List.of(OrderAction.START_PREPARING);
        }
        if (order.getStatus() == OrderStatus.PREPARING) {
            return List.of(OrderAction.SHIP);
        }
        return List.of();
    }
}
