package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Inquiry;
import com.dailyatelier.dailyatelier.entity.InquiryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InquiryAdminListResponseDto {
    private Long inquiryId;
    private String userId;
    private String nickname;
    private InquiryType inquiryType;
    private String title;
    private boolean answered;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;

    public static InquiryAdminListResponseDto from(Inquiry inquiry) {
        return new InquiryAdminListResponseDto(
                inquiry.getInquiryId(),
                inquiry.getUser().getUserId(),
                inquiry.getUser().getNickname(),
                inquiry.getInquiryType(),
                inquiry.getTitle(),
                inquiry.getAnsweredAt() != null,
                inquiry.getCreatedAt(),
                inquiry.getAnsweredAt()
        );
    }
}
