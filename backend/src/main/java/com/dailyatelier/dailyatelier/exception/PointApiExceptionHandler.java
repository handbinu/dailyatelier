package com.dailyatelier.dailyatelier.exception;

import com.dailyatelier.dailyatelier.controller.PointController;
import com.dailyatelier.dailyatelier.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PointController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PointApiExceptionHandler {
    @ExceptionHandler(PointApiException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePointException(
            PointApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(ApiErrorResponseDto.of(
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiErrorResponseDto> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.badRequest().body(ApiErrorResponseDto.of(
                status.value(),
                "INVALID_POINT_REQUEST",
                "충전 금액과 멱등성 키를 확인해 주세요.",
                request.getRequestURI()));
    }
}
