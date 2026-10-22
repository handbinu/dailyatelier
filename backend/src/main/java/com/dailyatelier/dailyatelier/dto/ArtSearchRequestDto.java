package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtSearchRequestDto {
    private String q;
    private String artist;
    private ArtFormat format;
    private ArtCategory category;
    private ArtSearchStatus status;
    private ArtSearchSort sort = ArtSearchSort.ENDING_SOON;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(50)
    private int size = 12;

    public ArtSearchCriteria toCriteria() {
        return new ArtSearchCriteria(q, artist, format, category, status, sort);
    }
}
