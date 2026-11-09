package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtistReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewSort;
import com.dailyatelier.dailyatelier.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists/me/reviews")
@RequiredArgsConstructor
public class ArtistReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ArtistReviewPageResponseDto> getReviews(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) Long artId,
            @RequestParam(defaultValue = "RECENT") ReviewSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(
                reviewService.getArtistReviews(
                        userId,
                        artId,
                        sort,
                        page,
                        size
                )
        );
    }
}
