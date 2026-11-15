package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ArtDetailResponseDto {
    private Long artId;
    private Long orderId;
    private String artistCode;
    private String artistName;
    private String name;
    private String descript;
    private String material;
    private ArtFormat format;
    private ArtCategory category;
    private String wIntro;
    private Integer startPrice;
    private Integer currentPrice;
    private Integer minimumBidIncrement;
    private Integer nextMinimumBidPrice;
    private LocalDateTime bidStartTime;
    private LocalDateTime closingTime;
    private String imgPath;
    private Integer artStatus;
    private Boolean isOwner;
}
