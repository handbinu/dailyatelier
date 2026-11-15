package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.OrderDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderPageResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderRefundRequestDto;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.service.OrderQueryService;
import com.dailyatelier.dailyatelier.service.OrderService;
import com.dailyatelier.dailyatelier.service.OrderStateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/users/me/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderStateService orderStateService;

    @GetMapping
    public ResponseEntity<OrderPageResponseDto> getOrders(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(
                orderQueryService.getBuyerOrders(
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
                orderQueryService.getBuyerOrder(userId, orderId)
        );
    }

    @PutMapping("/{orderId}/shipping-address")
    public ResponseEntity<OrderShippingAddressResponseDto> confirmShippingAddress(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderShippingAddressRequestDto request) {
        return ResponseEntity.ok(
                orderService.confirmShippingAddress(orderId, userId, request)
        );
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponseDto> cancelOrder(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(OrderDetailResponseDto.forBuyer(
                orderStateService.cancelPending(orderId, userId)
        ));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderDetailResponseDto> confirmOrder(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(OrderDetailResponseDto.forBuyer(
                orderStateService.confirm(orderId, userId)
        ));
    }

    @PostMapping("/{orderId}/delivered")
    public ResponseEntity<OrderDetailResponseDto> markDelivered(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(OrderDetailResponseDto.forBuyer(
                orderStateService.markDelivered(orderId, userId)
        ));
    }

    @PostMapping("/{orderId}/refund-request")
    public ResponseEntity<OrderDetailResponseDto> requestRefund(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRefundRequestDto request) {
        return ResponseEntity.ok(OrderDetailResponseDto.forBuyer(
                orderStateService.requestRefund(orderId, userId, request.getReason())
        ));
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<OrderDetailResponseDto> payOrder(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(OrderDetailResponseDto.forBuyer(
                orderStateService.markPaid(orderId, userId, idempotencyKey)
        ));
    }
}
