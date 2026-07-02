package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.service.ArtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arts")
@RequiredArgsConstructor
public class ArtController {
    private final ArtService artService;

    @PostMapping
    public ResponseEntity<ArtResponseDto> createArt(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ArtCreateRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(artService.createArt(userId, dto));
    }
}
