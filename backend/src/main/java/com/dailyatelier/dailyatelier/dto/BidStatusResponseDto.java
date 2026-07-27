package com.dailyatelier.dailyatelier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BidStatusResponseDto {
    private Long artId;
    private String artName;
    private String artistName;
    private String imgPath;
    private Integer myBidPrice;
    private Integer currentPrice;
    @JsonProperty("isLeading")
    private boolean isLeading;
    private String auctionStatus;
    private String bidResult;
    private LocalDateTime lastBidTime;
    private LocalDateTime bidStartTime;
    private LocalDateTime closingTime;
}
