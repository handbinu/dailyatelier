package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Inquiry;
import com.dailyatelier.dailyatelier.entity.InquiryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InquiryListResponseDto {
    private Long inquiryId;
    private InquiryType inquiryType;
    private String title;
    private boolean answered;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;

    public static InquiryListResponseDto from(Inquiry inquiry) {
        return new InquiryListResponseDto(
                inquiry.getInquiryId(),
                inquiry.getInquiryType(),
                inquiry.getTitle(),
                inquiry.getAnsweredAt() != null,
                inquiry.getCreatedAt(),
                inquiry.getAnsweredAt()
        );
    }
}
