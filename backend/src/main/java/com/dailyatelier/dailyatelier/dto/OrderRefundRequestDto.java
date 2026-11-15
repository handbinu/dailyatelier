package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderRefundRequestDto {
    @NotBlank(message = "환불 요청 사유는 필수입니다.")
    @Size(max = 200, message = "환불 요청 사유는 200자 이하여야 합니다.")
    private String reason;
}
