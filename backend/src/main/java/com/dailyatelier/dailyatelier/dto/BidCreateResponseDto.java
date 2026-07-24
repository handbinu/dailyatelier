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
}
