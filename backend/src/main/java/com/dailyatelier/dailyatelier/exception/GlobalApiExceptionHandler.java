package com.dailyatelier.dailyatelier.exception;

import com.dailyatelier.dailyatelier.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(DomainApiException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDomainApiException(
            DomainApiException exception,
            HttpServletRequest request) {
        return errorResponse(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponseDto> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "요청값을 확인해 주세요.",
                request
        );
    }

    private ResponseEntity<ApiErrorResponseDto> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponseDto.of(
                status.value(),
                code,
                message,
                request.getRequestURI()
        ));
    }
}
