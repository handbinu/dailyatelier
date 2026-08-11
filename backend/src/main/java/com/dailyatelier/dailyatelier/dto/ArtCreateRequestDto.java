package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ArtCreateRequestDto {
    @NotBlank
    private String name;

    private String descript;

    private String material;

    @NotNull
    private ArtFormat format;

    @NotNull
    private ArtCategory category;

    private String wIntro;

    @NotNull
    @Min(1)
    private Integer startPrice;

    @NotNull
    private LocalDateTime bidStartTime;

    @NotNull
    @Future
    private LocalDateTime closingTime;

    @NotBlank
    private String imgPath;

    private Integer artStatus = 0;
}
