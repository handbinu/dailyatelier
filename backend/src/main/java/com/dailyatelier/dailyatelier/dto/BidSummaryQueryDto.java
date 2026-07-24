package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BidSummaryQueryDto {
    private Long artId;
    private String artName;
    private String artistName;
    private String imgPath;
    private Integer myBidPrice;
    private Integer currentPrice;
    private LocalDateTime lastBidTime;
    private LocalDateTime bidStartTime;
    private LocalDateTime closingTime;
    private Integer artStatus;
}
