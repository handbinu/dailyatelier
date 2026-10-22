package com.dailyatelier.dailyatelier.dto;

import java.util.List;

public record ArtistReviewPageResponseDto(
        List<ReviewResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long totalReviewCount,
        long endedArtCount,
        Double averageStar,
        List<ReviewArtOptionDto> arts,
        long soldArtCount,
        long reviewedArtCount,
        long unreviewedArtCount,
        List<UnreviewedSoldArtDto> unreviewedSoldArts) {
}
