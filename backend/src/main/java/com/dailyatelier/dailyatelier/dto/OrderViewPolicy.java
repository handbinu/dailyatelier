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
            case PAID, PREPARING -> order.getRefundRequestStatus()
                    == com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus.REQUESTED
                    ? List.of()
                    : List.of(OrderAction.REQUEST_REFUND);
            case SHIPPED -> order.getRefundRequestStatus()
                    == com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus.REQUESTED
                    ? List.of()
                    : List.of(OrderAction.MARK_DELIVERED, OrderAction.REQUEST_REFUND);
            case DELIVERED -> order.getRefundRequestStatus()
                    == com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus.REQUESTED
                    ? List.of()
                    : List.of(OrderAction.CONFIRM, OrderAction.REQUEST_REFUND);
            default -> List.of();
        };
    }

    static List<OrderAction> sellerActions(Order order) {
        if (order.getRefundRequestStatus()
                == com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus.REQUESTED) {
            return List.of(OrderAction.APPROVE_REFUND, OrderAction.REJECT_REFUND);
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return List.of(OrderAction.START_PREPARING);
        }
        if (order.getStatus() == OrderStatus.PREPARING) {
            return List.of(OrderAction.SHIP);
        }
        return List.of();
    }
}
