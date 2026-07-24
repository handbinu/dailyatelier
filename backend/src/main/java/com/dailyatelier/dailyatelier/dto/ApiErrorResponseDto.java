package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiErrorResponseDto {
    private LocalDateTime timestamp;
    private int status;
    private String code;
    private String message;
    private String path;

    public static ApiErrorResponseDto of(
            int status,
            String code,
            String message,
            String path) {
        return new ApiErrorResponseDto(
                LocalDateTime.now(),
                status,
                code,
                message,
                path
        );
    }
}
