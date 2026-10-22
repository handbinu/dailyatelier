package com.dailyatelier.dailyatelier.exception;

public class AuctionCloseIntegrityException extends RuntimeException {

    public AuctionCloseIntegrityException(Long artId, Integer currentPrice, Integer winningPrice) {
        super("경매 마감 가격이 일치하지 않습니다. artId=%d, currentPrice=%s, winningPrice=%s"
                .formatted(artId, currentPrice, winningPrice));
    }
}
