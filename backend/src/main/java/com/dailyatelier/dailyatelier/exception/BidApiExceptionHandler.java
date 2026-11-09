package com.dailyatelier.dailyatelier.exception;

import com.dailyatelier.dailyatelier.controller.BidController;
import com.dailyatelier.dailyatelier.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BidController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BidApiExceptionHandler {

    @ExceptionHandler(BidApiException.class)
    public ResponseEntity<ApiErrorResponseDto> handleBidApiException(
            BidApiException exception,
            HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(ApiErrorResponseDto.of(
                status.value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponseDto> handleInvalidBidAmount(
            Exception exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiErrorResponseDto.of(
                status.value(),
                "INVALID_BID_AMOUNT",
                "입찰 금액은 1원 이상 21억 원 이하의 정수여야 합니다.",
                request.getRequestURI()
        ));
    }
}
