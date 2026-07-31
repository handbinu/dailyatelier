package com.dailyatelier.dailyatelier.exception;

import com.dailyatelier.dailyatelier.controller.OrderController;
import com.dailyatelier.dailyatelier.controller.SellerOrderController;
import com.dailyatelier.dailyatelier.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice(assignableTypes = {
        OrderController.class,
        SellerOrderController.class
})
public class OrderApiExceptionHandler {

    @ExceptionHandler(OrderApiException.class)
    public ResponseEntity<ApiErrorResponseDto> handleOrderApiException(
            OrderApiException exception,
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
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponseDto> handleInvalidShippingAddress(
            Exception exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        boolean shippingAddressRequest =
                request.getRequestURI().endsWith("/shipping-address");
        return ResponseEntity.status(status).body(ApiErrorResponseDto.of(
                status.value(),
                shippingAddressRequest
                        ? "INVALID_SHIPPING_ADDRESS"
                        : "INVALID_ORDER_REQUEST",
                shippingAddressRequest
                        ? "배송지 입력값을 확인해 주세요."
                        : "주문 요청값을 확인해 주세요.",
                request.getRequestURI()
        ));
    }
}
