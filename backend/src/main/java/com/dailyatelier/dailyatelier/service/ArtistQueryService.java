package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtistQueryService {
    private static final int ARTIST_USER_STATUS = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<Integer> PUBLIC_ART_STATUSES = List.of(
            Art.STATUS_ACTIVE,
            Art.STATUS_UNSOLD,
            Art.STATUS_SOLD
    );

    private final ArtistRepository artistRepository;
    private final ArtRepository artRepository;
    private final Clock clock;

    public Page<ArtistSummaryResponseDto> getArtists(String keyword, int page, int size) {
        return artistRepository.findPublicArtists(
                normalizeKeyword(keyword),
                ARTIST_USER_STATUS,
                Art.STATUS_ACTIVE,
                LocalDateTime.now(clock),
                createPageRequest(page, size)
        );
    }

    public ArtistDetailResponseDto getArtist(String artistId) {
        return artistRepository.findPublicArtistDetail(
                        artistId,
                        ARTIST_USER_STATUS,
                        Art.STATUS_ACTIVE,
                        LocalDateTime.now(clock)
                )
                .orElseThrow(this::artistNotFound);
    }

    public Page<ArtResponseDto> getArtistArts(String artistId, int page, int size) {
        if (artistRepository.findPublicArtistDetail(
                artistId,
                ARTIST_USER_STATUS,
                Art.STATUS_ACTIVE,
                LocalDateTime.now(clock)
        ).isEmpty()) {
            throw artistNotFound();
        }
        return artRepository.findPublicArtsByArtistId(
                artistId,
                PUBLIC_ART_STATUSES,
                createPageRequest(page, size)
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private PageRequest createPageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
    }

    private DomainApiException artistNotFound() {
        return new DomainApiException(
                HttpStatus.NOT_FOUND,
                "ARTIST_NOT_FOUND",
                "Artist not found"
        );
    }
}
