package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.Review;

public record ReviewWriteResponseDto(
        Long orderId,
        Long artId,
        String artName,
        String artImage,
        String artistName,
        Integer winningPrice,
        ReviewResponseDto review) {

    public static ReviewWriteResponseDto of(Order order, Review review) {
        return new ReviewWriteResponseDto(
                order.getOrderId(),
                order.getArtIdSnapshot(),
                order.getArtNameSnapshot(),
                order.getArtImageSnapshot(),
                order.getSellerArtistNameSnapshot(),
                order.getWinningPrice(),
                review == null ? null : ReviewResponseDto.from(review)
        );
    }
}
