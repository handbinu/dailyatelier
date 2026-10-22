package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointChargeStateTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 12, 0);

    @Test
    void supportsEveryAllowedTransition() {
        PointCharge paid = pending("paid");
        paid.approve(null, 1_000, 1L, NOW);
        assertThat(paid.getStatus()).isEqualTo(PointChargeStatus.PAID);
        paid.refund(2L, NOW.plusMinutes(1));
        assertThat(paid.getStatus()).isEqualTo(PointChargeStatus.REFUNDED);

        PointCharge failed = pending("failed");
        failed.fail("DECLINED", "거절", NOW);
        assertThat(failed.getStatus()).isEqualTo(PointChargeStatus.FAILED);

        PointCharge canceled = pending("canceled");
        canceled.cancel(NOW);
        assertThat(canceled.getStatus()).isEqualTo(PointChargeStatus.CANCELED);
    }

    @Test
    void rejectsForbiddenTransitionsAndAmountMismatch() {
        PointCharge charge = pending("invalid");
        assertThatThrownBy(() -> charge.approve(null, 999, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        charge.cancel(NOW);
        assertThatThrownBy(() -> charge.fail("LATE", null, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> charge.refund(2L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private PointCharge pending(String key) {
        return PointCharge.pending("member", PaymentProvider.INTERNAL,
                "merchant-" + key, 1_000, key, NOW);
    }
}
