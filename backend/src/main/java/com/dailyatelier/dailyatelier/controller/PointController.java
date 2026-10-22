package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.PointChargeRequestDto;
import com.dailyatelier.dailyatelier.dto.PointChargeResponseDto;
import com.dailyatelier.dailyatelier.dto.PointSummaryResponseDto;
import com.dailyatelier.dailyatelier.dto.PointTransactionResponseDto;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import com.dailyatelier.dailyatelier.payment.PaymentApproval;
import com.dailyatelier.dailyatelier.service.PointChargeService;
import com.dailyatelier.dailyatelier.service.PointQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/points")
@RequiredArgsConstructor
public class PointController {
    private final PointQueryService pointQueryService;
    private final PointChargeService pointChargeService;

    @GetMapping
    public PointSummaryResponseDto getSummary(@AuthenticationPrincipal String userId) {
        return pointQueryService.getSummary(userId);
    }

    @GetMapping("/transactions")
    public Page<PointTransactionResponseDto> getTransactions(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pointQueryService.getTransactions(userId, page, size);
    }

    @GetMapping("/charges")
    public Page<PointChargeResponseDto> getCharges(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pointQueryService.getCharges(userId, page, size);
    }

    @PostMapping("/charges")
    public ResponseEntity<PointChargeResponseDto> charge(
            @AuthenticationPrincipal String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PointChargeRequestDto request) {
        PointCharge charge = pointChargeService.create(
                userId, PaymentProvider.INTERNAL, request.amount(), idempotencyKey);
        PointCharge approved = pointChargeService.approve(
                charge.getChargeId(),
                new PaymentApproval(
                        PaymentProvider.INTERNAL,
                        null,
                        request.amount(),
                        request.amount(),
                        true));
        return ResponseEntity.ok(PointChargeResponseDto.from(approved));
    }
}
