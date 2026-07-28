package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtUpdateRequestDto;
import com.dailyatelier.dailyatelier.service.ArtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arts")
@RequiredArgsConstructor
public class ArtController {
    private final ArtService artService;

    @GetMapping
    public ResponseEntity<Page<ArtResponseDto>> getActiveArts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(artService.getActiveArts(page, size));
    }

    @GetMapping("/{artId}")
    public ResponseEntity<ArtDetailResponseDto> getArt(
            Authentication authentication,
            @PathVariable Long artId) {
        String userId = authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
        return ResponseEntity.ok(artService.getArt(artId, userId));
    }

    @PostMapping
    public ResponseEntity<ArtResponseDto> createArt(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ArtCreateRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(artService.createArt(userId, dto));
    }

    @PatchMapping("/{artId}")
    public ResponseEntity<ArtResponseDto> updateArt(
            @AuthenticationPrincipal String userId,
            @PathVariable Long artId,
            @Valid @RequestBody ArtUpdateRequestDto dto) {
        return ResponseEntity.ok(artService.updateArt(artId, userId, dto));
    }

    @DeleteMapping("/{artId}")
    public ResponseEntity<ArtDeleteResponseDto> deleteArt(
            @AuthenticationPrincipal String userId,
            @PathVariable Long artId) {
        return ResponseEntity.ok(artService.deleteArt(artId, userId));
    }
}
