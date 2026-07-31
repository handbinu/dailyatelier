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
    private LocalDateTime bidTime;
    private long availablePoint;
    private long heldPoint;

    public BidCreateResponseDto(
            Long bidId,
            Long artId,
            Integer bidPrice,
            Integer currentPrice,
            LocalDateTime bidTime) {
        this(bidId, artId, bidPrice, currentPrice, bidTime, 0L, 0L);
    }
}
