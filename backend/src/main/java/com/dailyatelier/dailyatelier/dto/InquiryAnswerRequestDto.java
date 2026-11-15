package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InquiryAnswerRequestDto {
    @NotBlank(message = "답변 내용을 입력해 주세요.")
    @Size(max = 1000, message = "답변은 1000자 이하여야 합니다.")
    private String answer;
}
