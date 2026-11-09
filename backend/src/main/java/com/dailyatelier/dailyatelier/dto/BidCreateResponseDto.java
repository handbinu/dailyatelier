package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BidCreateResponseDto {
    private Long bidId;
    private Long artId;
    private Integer bidPrice;
    private Integer currentPrice;
    private Integer minimumBidIncrement;
    private Integer nextMinimumBidPrice;
    private LocalDateTime bidTime;
    private long availablePoint;
    private long heldPoint;

    public BidCreateResponseDto(
            Long bidId,
            Long artId,
            Integer bidPrice,
            Integer currentPrice,
            LocalDateTime bidTime) {
        this(
                bidId,
                artId,
                bidPrice,
                currentPrice,
                null,
                null,
                bidTime,
                0L,
                0L
        );
    }
}
