package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressResponseDto;
import com.dailyatelier.dailyatelier.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PutMapping("/{orderId}/shipping-address")
    public ResponseEntity<OrderShippingAddressResponseDto> confirmShippingAddress(
            @AuthenticationPrincipal String userId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderShippingAddressRequestDto request) {
        return ResponseEntity.ok(
                orderService.confirmShippingAddress(orderId, userId, request)
        );
    }
}
