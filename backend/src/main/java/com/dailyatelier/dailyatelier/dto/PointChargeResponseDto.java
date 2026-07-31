package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.PointCharge;
import java.time.LocalDateTime;

public record PointChargeResponseDto(
        Long chargeId,
        String merchantOrderId,
        long requestedAmount,
        long paidAmount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {
    public static PointChargeResponseDto from(PointCharge charge) {
        return new PointChargeResponseDto(
                charge.getChargeId(),
                charge.getMerchantOrderId(),
                charge.getRequestedAmount(),
                charge.getPaidAmount(),
                charge.getStatus().name(),
                charge.getCreatedAt(),
                charge.getPaidAt());
    }
}
