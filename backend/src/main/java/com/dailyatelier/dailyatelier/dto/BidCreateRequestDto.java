package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BidCreateRequestDto {
    @NotNull
    @Min(1)
    @Max(2_100_000_000)
    private Integer bidPrice;
}
