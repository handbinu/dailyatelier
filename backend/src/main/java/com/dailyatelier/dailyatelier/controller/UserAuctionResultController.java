package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.service.AuctionResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/wins")
@RequiredArgsConstructor
public class UserAuctionResultController {
    private final AuctionResultService auctionResultService;

    @GetMapping
    public ResponseEntity<Page<WinningArtResponseDto>> getMyWins(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(auctionResultService.getMyWins(userId, page, size));
    }
}
