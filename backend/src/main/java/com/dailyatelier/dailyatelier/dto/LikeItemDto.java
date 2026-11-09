package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LikeItemDto {
    private Long likeId;
    private Long artId;
    private String artName;
    private String artImg;
    private String artistName;
    private Integer currentPrice;
    private LocalDateTime closingTime;
    private Integer artStatus;
    private Boolean artDeleted;
    private String availabilityMessage;
}
