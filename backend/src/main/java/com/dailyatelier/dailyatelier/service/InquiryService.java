package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.InquiryAdminListResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryAnswerRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryListResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryStatus;
import com.dailyatelier.dailyatelier.entity.Inquiry;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.InquiryRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InquiryService {
    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public InquiryDetailResponseDto createInquiry(
            String userId,
            InquiryCreateRequestDto request,
            MultipartFile attachment) {
        User user = findUser(userId);
        Inquiry inquiry = new Inquiry();
        inquiry.setUser(user);
        inquiry.setInquiryType(request.getInquiryType());
        inquiry.setTitle(request.getTitle().trim());
        inquiry.setContent(request.getContent().trim());
        inquiry.setEmailAlert(request.isEmailAlert());

        if (attachment != null && !attachment.isEmpty()) {
            CloudinaryService.InquiryAttachment uploaded = cloudinaryService.uploadInquiryAttachment(userId, attachment);
            inquiry.setAttachmentUrl(uploaded.url());
            inquiry.setAttachmentName(uploaded.originalFilename());
            inquiry.setAttachmentResourceType(uploaded.resourceType());
        }

        return InquiryDetailResponseDto.from(inquiryRepository.save(inquiry));
    }

    @Transactional(readOnly = true)
    public Page<InquiryListResponseDto> getMyInquiries(String userId, InquiryStatus status, Pageable pageable) {
        Page<Inquiry> inquiries = switch (status) {
            case PENDING -> inquiryRepository.findByUser_UserIdAndAnsweredAtIsNullOrderByCreatedAtDesc(userId, pageable);
            case ANSWERED -> inquiryRepository.findByUser_UserIdAndAnsweredAtIsNotNullOrderByCreatedAtDesc(userId, pageable);
            case ALL -> inquiryRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
        };
        return inquiries.map(InquiryListResponseDto::from);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponseDto getInquiryDetail(String userId, boolean isAdmin, Long inquiryId) {
        Inquiry inquiry = findInquiry(inquiryId);
        if (!isAdmin && !inquiry.getUser().getUserId().equals(userId)) {
            throw new DomainApiException(HttpStatus.FORBIDDEN, "INQUIRY_ACCESS_FORBIDDEN", "해당 문의에 접근할 권한이 없습니다.");
        }
        return InquiryDetailResponseDto.from(inquiry);
    }

    @Transactional(readOnly = true)
    public Page<InquiryAdminListResponseDto> getAdminInquiries(InquiryStatus status, Pageable pageable) {
        Page<Inquiry> inquiries = switch (status) {
            case PENDING -> inquiryRepository.findByAnsweredAtIsNullOrderByCreatedAtDesc(pageable);
            case ANSWERED -> inquiryRepository.findByAnsweredAtIsNotNullOrderByCreatedAtDesc(pageable);
            case ALL -> inquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
        };
        return inquiries.map(InquiryAdminListResponseDto::from);
    }

    @Transactional
    public InquiryDetailResponseDto answerInquiry(Long inquiryId, InquiryAnswerRequestDto request) {
        Inquiry inquiry = findInquiry(inquiryId);
        if (inquiry.getAnsweredAt() != null) {
            throw new DomainApiException(HttpStatus.CONFLICT, "INQUIRY_ALREADY_ANSWERED", "이미 답변이 등록된 문의입니다.");
        }
        inquiry.setAnswer(request.getAnswer().trim());
        inquiry.setAnsweredAt(LocalDateTime.now());
        return InquiryDetailResponseDto.from(inquiry);
    }

    private User findUser(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new DomainApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private Inquiry findInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new DomainApiException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));
    }
}
