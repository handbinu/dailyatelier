package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;

public record ArtSearchCriteria(
        String query,
        String artist,
        ArtFormat format,
        ArtCategory category,
        ArtSearchStatus status,
        ArtSearchSort sort
) {
}
