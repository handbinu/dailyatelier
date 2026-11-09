package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointLedgerEntityTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);

    @Test
    void pointAccountRejectsNegativeOpeningBalance() {
        User user = new User();
        user.setUserId("member");

        assertThatThrownBy(() -> PointAccount.open(user, -1L, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    void pointTransactionRejectsNonPositiveAmountAndNegativeBalance() {
        assertThatThrownBy(() -> PointTransaction.openingBalance(
                "member",
                0L,
                now
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");

        assertThatThrownBy(() -> PointTransaction.record(
                "member",
                PointTransactionType.ADJUSTMENT_DEBIT,
                1L,
                -1L,
                0L,
                -1L,
                0L,
                PointReferenceType.USER,
                "member",
                "negative-after",
                null,
                null,
                null,
                now
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액");
    }

    @Test
    void holdAndChargeRejectNonPositiveAmounts() {
        assertThatThrownBy(() -> PointHold.hold(
                null,
                null,
                null,
                0L,
                now
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");

        assertThatThrownBy(() -> PointCharge.pending(
                "member",
                PaymentProvider.INTERNAL,
                "merchant-1",
                -1L,
                "charge-1",
                now
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }
}
