package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.LikeItemDto;
import com.dailyatelier.dailyatelier.dto.LikeStatusDto;
import com.dailyatelier.dailyatelier.service.LikesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LikesController {
    private final LikesService likesService;

    @GetMapping("/users/me/likes")
    public ResponseEntity<Page<LikeItemDto>> getMyLikes(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(likesService.getMyLikes(userId, pageable));
    }

    @GetMapping("/arts/{artId}/like")
    public ResponseEntity<LikeStatusDto> getLikeStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable Long artId) {
        return ResponseEntity.ok(likesService.getLikeStatus(userId, artId));
    }

    @PostMapping("/arts/{artId}/like")
    public ResponseEntity<LikeStatusDto> addLike(
            @AuthenticationPrincipal String userId,
            @PathVariable Long artId) {
        return ResponseEntity.ok(likesService.addLike(userId, artId));
    }

    @DeleteMapping("/arts/{artId}/like")
    public ResponseEntity<LikeStatusDto> removeLike(
            @AuthenticationPrincipal String userId,
            @PathVariable Long artId) {
        return ResponseEntity.ok(likesService.removeLike(userId, artId));
    }
}
