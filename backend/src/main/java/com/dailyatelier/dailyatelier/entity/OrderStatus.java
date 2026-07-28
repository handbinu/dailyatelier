package com.dailyatelier.dailyatelier.entity;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CONFIRMED,
    CANCELED,
    REFUNDED;

    private static final Set<OrderStatus> TERMINAL_STATUSES =
            EnumSet.of(CONFIRMED, CANCELED, REFUNDED);

    public boolean canTransitionTo(OrderStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PAYMENT_PENDING -> nextStatus == PAID || nextStatus == CANCELED;
            case PAID -> nextStatus == PREPARING || nextStatus == REFUNDED;
            case PREPARING -> nextStatus == SHIPPED || nextStatus == REFUNDED;
            case SHIPPED -> nextStatus == DELIVERED;
            case DELIVERED -> nextStatus == CONFIRMED;
            case CONFIRMED, CANCELED, REFUNDED -> false;
        };
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }
}
