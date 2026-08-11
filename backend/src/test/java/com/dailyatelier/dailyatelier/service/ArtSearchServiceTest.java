package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.dto.ArtSearchResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtSearchSort;
import com.dailyatelier.dailyatelier.dto.ArtSearchStatus;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtSearchServiceTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 11, 12, 0);

    @Mock
    private ArtRepository artRepository;

    @Test
    void normalizesTextUsesClockMapsStatusAndCapsPageSize() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-11T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        ArtSearchService service = new ArtSearchService(artRepository, clock);
        Art art = art();
        when(artRepository.search(any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(art), invocation.getArgument(2), 1
                ));

        Page<ArtSearchResponseDto> result = service.search(
                new ArtSearchCriteria(
                        "  테스트  ", "   ", null, null, null,
                        ArtSearchSort.NEWEST
                ), -1, 100
        );

        ArgumentCaptor<ArtSearchCriteria> criteria =
                ArgumentCaptor.forClass(ArtSearchCriteria.class);
        ArgumentCaptor<LocalDateTime> now =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(artRepository).search(
                criteria.capture(), now.capture(), pageable.capture()
        );
        assertThat(criteria.getValue().query()).isEqualTo("테스트");
        assertThat(criteria.getValue().artist()).isNull();
        assertThat(now.getValue()).isEqualTo(NOW);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(result.getContent().get(0).status())
                .isEqualTo(ArtSearchStatus.ONGOING);
    }

    private Art art() {
        Artist artist = new Artist();
        artist.setArtistCode("artist-code");
        artist.setArtistName("작가");
        Art art = new Art();
        art.setArtId(1L);
        art.setArtist(artist);
        art.setName("테스트 작품");
        art.setFormat(ArtFormat.PHYSICAL);
        art.setCategory(ArtCategory.OTHER);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(NOW.minusHours(1));
        art.setClosingTime(NOW.plusHours(1));
        art.setImgPath("image.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art.setCreatedAt(NOW.minusDays(1));
        return art;
    }
}
