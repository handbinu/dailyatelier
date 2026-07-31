package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.PointTransaction;
import java.time.LocalDateTime;

public record PointTransactionResponseDto(
        Long transactionId,
        String type,
        long amount,
        long availableDelta,
        long heldDelta,
        long availableBalanceAfter,
        long heldBalanceAfter,
        String description,
        LocalDateTime createdAt) {
    public static PointTransactionResponseDto from(PointTransaction transaction) {
        return new PointTransactionResponseDto(
                transaction.getTransactionId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getAvailableDelta(),
                transaction.getHeldDelta(),
                transaction.getAvailableBalanceAfter(),
                transaction.getHeldBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
