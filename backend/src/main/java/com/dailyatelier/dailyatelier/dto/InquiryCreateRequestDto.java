package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.InquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InquiryCreateRequestDto {
    @NotNull(message = "문의 유형을 선택해 주세요.")
    private InquiryType inquiryType;

    @NotBlank(message = "문의 제목을 입력해 주세요.")
    @Size(max = 100, message = "문의 제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "문의 내용을 입력해 주세요.")
    @Size(min = 10, max = 1000, message = "문의 내용은 10자 이상 1000자 이하로 입력해 주세요.")
    private String content;

    private boolean emailAlert = true;
}
