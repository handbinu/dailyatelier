package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.dto.ArtSearchResult;
import com.dailyatelier.dailyatelier.dto.ArtSearchResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtSearchStatus;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArtSearchService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ArtRepository artRepository;
    private final Clock clock;

    public Page<ArtSearchResponseDto> search(
            ArtSearchCriteria criteria,
            int page,
            int size) {
        ArtSearchCriteria normalized = new ArtSearchCriteria(
                blankToNull(criteria.query()),
                blankToNull(criteria.artist()),
                criteria.format(),
                criteria.category(),
                criteria.status(),
                criteria.sort()
        );
        LocalDateTime now = LocalDateTime.now(clock);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
        return artRepository.search(normalized, now, pageable)
                .map(art -> toResponse(art, now));
    }

    private ArtSearchResponseDto toResponse(Art art, LocalDateTime now) {
        Artist artist = art.getArtist();
        return new ArtSearchResponseDto(
                art.getArtId(), artist.getArtistCode(), artist.getArtistName(),
                art.getName(), art.getFormat(), art.getCategory(),
                art.getCurrentPrice(), art.getBidStartTime(),
                art.getClosingTime(), art.getImgPath(), status(art, now),
                result(art), art.getCreatedAt()
        );
    }

    private ArtSearchResult result(Art art) {
        return switch (art.getArtStatus()) {
            case Art.STATUS_SOLD -> ArtSearchResult.SOLD;
            case Art.STATUS_UNSOLD -> ArtSearchResult.UNSOLD;
            default -> null;
        };
    }

    private ArtSearchStatus status(Art art, LocalDateTime now) {
        if (art.getArtStatus() == Art.STATUS_UNSOLD
                || art.getArtStatus() == Art.STATUS_SOLD
                || !art.getClosingTime().isAfter(now)) {
            return ArtSearchStatus.ENDED;
        }
        return art.getBidStartTime().isAfter(now)
                ? ArtSearchStatus.UPCOMING
                : ArtSearchStatus.ONGOING;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
