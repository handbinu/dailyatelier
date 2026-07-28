package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void allowsOnlyDefinedTransitions() {
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAID))
                .isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CANCELED))
                .isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PREPARING))
                .isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED))
                .isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.SHIPPED))
                .isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.REFUNDED))
                .isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED))
                .isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CONFIRMED))
                .isTrue();

        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.SHIPPED))
                .isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELED))
                .isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDED))
                .isFalse();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PAYMENT_PENDING))
                .isFalse();
        assertThat(OrderStatus.CANCELED.canTransitionTo(OrderStatus.PAID))
                .isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PAID))
                .isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(null)).isFalse();
    }

    @Test
    void identifiesTerminalStatuses() {
        assertThat(OrderStatus.CONFIRMED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.isTerminal()).isFalse();
        assertThat(OrderStatus.SHIPPED.isTerminal()).isFalse();
    }
}
