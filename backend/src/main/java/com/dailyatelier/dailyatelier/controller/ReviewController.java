package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ReviewCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewSort;
import com.dailyatelier.dailyatelier.dto.ReviewUpdateRequestDto;
import com.dailyatelier.dailyatelier.dto.ReviewWriteResponseDto;
import com.dailyatelier.dailyatelier.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/orders/{orderId}/review")
    public ResponseEntity<ReviewWriteResponseDto> getWriteReview(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(reviewService.getWriteReview(userId, orderId));
    }

    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponseDto> createReview(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ReviewCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(userId, request));
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponseDto> updateReview(
            @AuthenticationPrincipal String userId,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequestDto request) {
        return ResponseEntity.ok(reviewService.update(userId, reviewId, request));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ReviewPageResponseDto> getMyReviews(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "RECENT") ReviewSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(
                reviewService.getMyReviews(userId, sort, page, size)
        );
    }
}
