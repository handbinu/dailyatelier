package com.dailyatelier.dailyatelier.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionPricePolicyTest {

    @Test
    void calculatesNextMinimumBidPriceWithoutIntegerOverflow() {
        assertThat(AuctionPricePolicy.nextMinimumBidPrice(2_099_999_000, 1_000))
                .contains(2_100_000_000);
        assertThat(AuctionPricePolicy.nextMinimumBidPrice(2_100_000_000, 1_000))
                .isEmpty();
        assertThat(AuctionPricePolicy.nextMinimumBidPrice(Integer.MAX_VALUE, 10_000_000))
                .isEmpty();
    }
}
