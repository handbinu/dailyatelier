package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequestDto {
    @NotNull
    @Min(1)
    @Max(10)
    private Integer star;

    @NotBlank
    @Size(min = 10, max = 300)
    private String content;
}
