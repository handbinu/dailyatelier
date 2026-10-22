package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.OrderDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderPageResponseDto;
import com.dailyatelier.dailyatelier.dto.SellerOrderStatusUpdateRequestDto;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.service.OrderQueryService;
import com.dailyatelier.dailyatelier.service.OrderStateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/artists/me/orders")
@RequiredArgsConstructor
public class SellerOrderController {
    private final OrderQueryService orderQueryService;
    private final OrderStateService orderStateService;

    @GetMapping
    public ResponseEntity<OrderPageResponseDto> getOrders(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(
                orderQueryService.getSellerOrders(
                        userId,
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponseDto> getOrder(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(
                orderQueryService.getSellerOrder(userId, orderId)
        );
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderDetailResponseDto> updateStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @Valid @RequestBody SellerOrderStatusUpdateRequestDto request) {
        Order updatedOrder = switch (request.getStatus()) {
            case PREPARING -> orderStateService.startPreparing(orderId, userId);
            case SHIPPED -> orderStateService.ship(
                    orderId,
                    userId,
                    request.getShippingCarrier(),
                    request.getTrackingNumber()
            );
            default -> throw new OrderApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SELLER_ORDER_STATUS",
                    "작가는 상품 준비 또는 발송 상태만 처리할 수 있습니다."
            );
        };
        return ResponseEntity.ok(
                OrderDetailResponseDto.forSeller(updatedOrder)
        );
    }

    @PostMapping("/{orderId}/refund/approve")
    public ResponseEntity<OrderDetailResponseDto> approveRefund(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(OrderDetailResponseDto.forSeller(
                orderStateService.approveRefund(orderId, userId, idempotencyKey)
        ));
    }

    @PostMapping("/{orderId}/refund/reject")
    public ResponseEntity<OrderDetailResponseDto> rejectRefund(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(OrderDetailResponseDto.forSeller(
                orderStateService.rejectRefund(orderId, userId)
        ));
    }
}
