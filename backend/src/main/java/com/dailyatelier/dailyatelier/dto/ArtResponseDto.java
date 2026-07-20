package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ArtResponseDto {
    private Long artId;
    private String artistCode;
    private String artistName;
    private String name;
    private String descript;
    private String material;
    private String wIntro;
    private Integer startPrice;
    private Integer currentPrice;
    private LocalDateTime bidStartTime;
    private LocalDateTime closingTime;
    private String imgPath;
    private Integer artStatus;
}
