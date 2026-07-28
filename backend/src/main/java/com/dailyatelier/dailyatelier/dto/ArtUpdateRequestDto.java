package com.dailyatelier.dailyatelier.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ArtUpdateRequestDto {
    @Min(1)
    @Max(2_100_000_000)
    private Integer startPrice;

    private LocalDateTime bidStartTime;

    private LocalDateTime closingTime;

    @Size(max = 300)
    private String descript;

    @Size(max = 120)
    private String material;

    @Size(max = 500)
    private String wIntro;

    @Size(max = 2048)
    private String imgPath;

    @JsonIgnore
    private boolean startPriceProvided;

    @JsonIgnore
    private boolean bidStartTimeProvided;

    @JsonIgnore
    private boolean closingTimeProvided;

    @JsonIgnore
    private boolean descriptProvided;

    @JsonIgnore
    private boolean materialProvided;

    @JsonIgnore
    private boolean wIntroProvided;

    @JsonIgnore
    private boolean imgPathProvided;

    @JsonSetter("startPrice")
    public void setStartPrice(Integer startPrice) {
        this.startPriceProvided = true;
        this.startPrice = startPrice;
    }

    @JsonSetter("bidStartTime")
    public void setBidStartTime(LocalDateTime bidStartTime) {
        this.bidStartTimeProvided = true;
        this.bidStartTime = bidStartTime;
    }

    @JsonSetter("closingTime")
    public void setClosingTime(LocalDateTime closingTime) {
        this.closingTimeProvided = true;
        this.closingTime = closingTime;
    }

    @JsonSetter("descript")
    public void setDescript(String descript) {
        this.descriptProvided = true;
        this.descript = descript;
    }

    @JsonSetter("material")
    public void setMaterial(String material) {
        this.materialProvided = true;
        this.material = material;
    }

    @JsonSetter("wIntro")
    public void setWIntro(String wIntro) {
        this.wIntroProvided = true;
        this.wIntro = wIntro;
    }

    @JsonSetter("imgPath")
    public void setImgPath(String imgPath) {
        this.imgPathProvided = true;
        this.imgPath = imgPath;
    }

    @JsonIgnore
    @AssertTrue(message = "수정할 필드를 하나 이상 입력해야 합니다.")
    public boolean isAnyFieldProvided() {
        return startPriceProvided
                || bidStartTimeProvided
                || closingTimeProvided
                || descriptProvided
                || materialProvided
                || wIntroProvided
                || imgPathProvided;
    }

    @JsonIgnore
    @AssertTrue(message = "시작가는 필수입니다.")
    public boolean isProvidedStartPriceValid() {
        return !startPriceProvided || startPrice != null;
    }

    @JsonIgnore
    @AssertTrue(message = "경매 시작 시각은 필수입니다.")
    public boolean isProvidedBidStartTimeValid() {
        return !bidStartTimeProvided || bidStartTime != null;
    }

    @JsonIgnore
    @AssertTrue(message = "경매 마감 시각은 필수입니다.")
    public boolean isProvidedClosingTimeValid() {
        return !closingTimeProvided || closingTime != null;
    }

    @JsonIgnore
    @AssertTrue(message = "이미지 경로는 비어 있을 수 없습니다.")
    public boolean isProvidedImgPathValid() {
        return !imgPathProvided || imgPath != null && !imgPath.isBlank();
    }
}
