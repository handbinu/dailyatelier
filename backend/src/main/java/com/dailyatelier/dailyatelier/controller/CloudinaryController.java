package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.CloudinarySignatureRequestDto;
import com.dailyatelier.dailyatelier.dto.CloudinarySignatureResponseDto;
import com.dailyatelier.dailyatelier.service.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads/cloudinary")
@RequiredArgsConstructor
public class CloudinaryController {
    private final CloudinaryService cloudinaryService;

    @PostMapping("/signature")
    public ResponseEntity<CloudinarySignatureResponseDto> createSignature(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CloudinarySignatureRequestDto dto) {
        return ResponseEntity.ok(cloudinaryService.createUploadSignature(userId, dto.getFolder()));
    }
}
