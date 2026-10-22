package com.dailyatelier.dailyatelier.service;

import java.util.Optional;

public final class AuctionPricePolicy {
    public static final int MAX_BID_PRICE = 2_100_000_000;
    public static final int DEFAULT_MINIMUM_BID_INCREMENT = 1_000;
    public static final int MINIMUM_BID_INCREMENT = 100;
    public static final int MAXIMUM_BID_INCREMENT = 10_000_000;
    public static final int BID_INCREMENT_UNIT = 100;

    private AuctionPricePolicy() {
    }

    public static boolean isValidMinimumBidIncrement(Integer increment) {
        return increment != null
                && increment >= MINIMUM_BID_INCREMENT
                && increment <= MAXIMUM_BID_INCREMENT
                && increment % BID_INCREMENT_UNIT == 0;
    }

    public static Optional<Integer> nextMinimumBidPrice(
            Integer currentPrice,
            Integer minimumBidIncrement) {
        if (currentPrice == null || minimumBidIncrement == null) {
            return Optional.empty();
        }
        long nextPrice = (long) currentPrice + minimumBidIncrement;
        return nextPrice <= MAX_BID_PRICE
                ? Optional.of((int) nextPrice)
                : Optional.empty();
    }
}
