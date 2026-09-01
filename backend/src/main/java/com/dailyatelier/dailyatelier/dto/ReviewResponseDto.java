package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponseDto(
        Long reviewId,
        Long orderId,
        Long artId,
        String artName,
        String artImage,
        String artistName,
        String buyerNickname,
        Integer winningPrice,
        Integer star,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ReviewResponseDto from(Review review) {
        return new ReviewResponseDto(
                review.getReviewId(),
                review.getOrder().getOrderId(),
                review.getArt().getArtId(),
                review.getOrder().getArtNameSnapshot(),
                review.getOrder().getArtImageSnapshot(),
                review.getOrder().getSellerArtistNameSnapshot(),
                review.getUser().getNickname(),
                review.getOrder().getWinningPrice(),
                review.getStar(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
