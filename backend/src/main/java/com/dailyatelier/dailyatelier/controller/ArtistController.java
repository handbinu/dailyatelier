package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.service.ArtistQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistQueryService artistQueryService;

    @GetMapping
    public ResponseEntity<Page<ArtistSummaryResponseDto>> getArtists(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(artistQueryService.getArtists(keyword, page, size));
    }

    @GetMapping("/{artistId}")
    public ResponseEntity<ArtistDetailResponseDto> getArtist(
            @PathVariable String artistId) {
        return ResponseEntity.ok(artistQueryService.getArtist(artistId));
    }

    @GetMapping("/{artistId}/arts")
    public ResponseEntity<Page<ArtResponseDto>> getArtistArts(
            @PathVariable String artistId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(artistQueryService.getArtistArts(artistId, page, size));
    }
}
