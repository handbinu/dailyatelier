package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.InquiryAdminListResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryAnswerRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryStatus;
import com.dailyatelier.dailyatelier.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {
    private final InquiryService inquiryService;

    @GetMapping
    public ResponseEntity<Page<InquiryAdminListResponseDto>> getInquiries(
            @RequestParam(defaultValue = "ALL") InquiryStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(inquiryService.getAdminInquiries(status, normalizePageable(pageable)));
    }

    @PostMapping("/{inquiryId}/answer")
    public ResponseEntity<InquiryDetailResponseDto> answerInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequestDto request) {
        return ResponseEntity.ok(inquiryService.answerInquiry(inquiryId, request));
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        return PageRequest.of(page, size, pageable.getSort());
    }
}
