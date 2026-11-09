package com.dailyatelier.dailyatelier.dto;

import java.util.List;

public record ReviewPageResponseDto(
        List<ReviewResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
