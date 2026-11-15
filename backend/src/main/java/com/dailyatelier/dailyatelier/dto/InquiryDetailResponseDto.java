package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Inquiry;
import com.dailyatelier.dailyatelier.entity.InquiryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InquiryDetailResponseDto {
    private Long inquiryId;
    private String userId;
    private InquiryType inquiryType;
    private String title;
    private String content;
    private boolean emailAlert;
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentResourceType;
    private boolean answered;
    private String answer;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;

    public static InquiryDetailResponseDto from(Inquiry inquiry) {
        return new InquiryDetailResponseDto(
                inquiry.getInquiryId(),
                inquiry.getUser().getUserId(),
                inquiry.getInquiryType(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.isEmailAlert(),
                inquiry.getAttachmentUrl(),
                inquiry.getAttachmentName(),
                inquiry.getAttachmentResourceType(),
                inquiry.getAnsweredAt() != null,
                inquiry.getAnswer(),
                inquiry.getCreatedAt(),
                inquiry.getAnsweredAt()
        );
    }
}
