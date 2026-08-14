package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.InquiryCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.InquiryDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.InquiryListResponseDto;
import com.dailyatelier.dailyatelier.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {
    private final InquiryService inquiryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InquiryDetailResponseDto> createInquiry(
            @AuthenticationPrincipal String userId,
            @Valid @RequestPart("request") InquiryCreateRequestDto request,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiryService.createInquiry(userId, request, attachment));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<InquiryListResponseDto>> getMyInquiries(
            @AuthenticationPrincipal String userId,
            Pageable pageable) {
        return ResponseEntity.ok(inquiryService.getMyInquiries(userId, normalizePageable(pageable)));
    }

    @GetMapping("/{inquiryId}")
    public ResponseEntity<InquiryDetailResponseDto> getInquiryDetail(
            @AuthenticationPrincipal String userId,
            Authentication authentication,
            @PathVariable Long inquiryId) {
        return ResponseEntity.ok(inquiryService.getInquiryDetail(userId, isAdmin(authentication), inquiryId));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        return PageRequest.of(page, size, pageable.getSort());
    }
}
