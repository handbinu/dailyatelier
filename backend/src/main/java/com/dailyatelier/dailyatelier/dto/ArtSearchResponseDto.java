package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;

import java.time.LocalDateTime;

public record ArtSearchResponseDto(
        Long artId,
        String artistCode,
        String artistName,
        String name,
        ArtFormat format,
        ArtCategory category,
        Integer currentPrice,
        LocalDateTime bidStartTime,
        LocalDateTime closingTime,
        String imgPath,
        ArtSearchStatus status,
        ArtSearchResult result,
        LocalDateTime createdAt
) {
}
