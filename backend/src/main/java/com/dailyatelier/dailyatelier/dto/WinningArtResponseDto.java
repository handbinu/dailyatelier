package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class WinningArtResponseDto {
    private Long artId;
    private String artName;
    private String artistName;
    private String imgPath;
    private Integer winningPrice;
    private LocalDateTime closedAt;
}
