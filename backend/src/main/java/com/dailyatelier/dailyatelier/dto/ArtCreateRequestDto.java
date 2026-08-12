package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.service.AuctionPricePolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
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
    @Max(AuctionPricePolicy.MAX_BID_PRICE)
    private Integer startPrice;

    @NotNull
    private Integer minimumBidIncrement = AuctionPricePolicy.DEFAULT_MINIMUM_BID_INCREMENT;

    @NotNull
    private LocalDateTime bidStartTime;

    @NotNull
    @Future
    private LocalDateTime closingTime;

    @NotBlank
    private String imgPath;

    private Integer artStatus = 0;

    @AssertTrue(message = "최소 입찰 증분은 100원 이상 10,000,000원 이하의 100원 단위여야 합니다.")
    public boolean isMinimumBidIncrementValid() {
        return minimumBidIncrement == null
                || AuctionPricePolicy.isValidMinimumBidIncrement(minimumBidIncrement);
    }
}
