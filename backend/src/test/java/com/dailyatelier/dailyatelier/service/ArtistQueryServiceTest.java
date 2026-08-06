package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistQueryServiceTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private ArtRepository artRepository;

    private ArtistQueryService artistQueryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-05T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        artistQueryService = new ArtistQueryService(artistRepository, artRepository, clock);
    }

    @Test
    void trimsKeywordAndCapsPaginationWhilePassingFixedCurrentTime() {
        when(artistRepository.findPublicArtists(
                eq("Alpha"), eq(1), eq(Art.STATUS_ACTIVE), eq(NOW), any(Pageable.class)))
                .thenReturn(Page.empty());

        artistQueryService.getArtists("  Alpha  ", -1, 100);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artistRepository).findPublicArtists(
                eq("Alpha"), eq(1), eq(Art.STATUS_ACTIVE), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void convertsNullAndBlankKeywordsToUnfilteredSearch() {
        when(artistRepository.findPublicArtists(
                eq(null), eq(1), eq(Art.STATUS_ACTIVE), eq(NOW), any(Pageable.class)))
                .thenReturn(Page.empty());

        artistQueryService.getArtists(null, 0, 12);
        artistQueryService.getArtists("   ", 0, 12);

        verify(artistRepository, org.mockito.Mockito.times(2)).findPublicArtists(
                eq(null), eq(1), eq(Art.STATUS_ACTIVE), eq(NOW), any(Pageable.class));
    }

    @Test
    void returnsDetailWithDefaultProfileImageContract() {
        ArtistDetailResponseDto detail = new ArtistDetailResponseDto(
                "artist-code", "작가", "전체 소개", 3L);
        when(artistRepository.findPublicArtistDetail(
                "artist-code", 1, Art.STATUS_ACTIVE, NOW))
                .thenReturn(Optional.of(detail));

        ArtistDetailResponseDto result = artistQueryService.getArtist("artist-code");

        assertThat(result.getProfileImagePath()).isEqualTo("/img/artist.png");
        assertThat(result.getArtistIntro()).isEqualTo("전체 소개");
        assertThat(result.getActiveArtCount()).isEqualTo(3);
    }

    @Test
    void missingOrNonArtistProfileUsesArtistNotFoundContract() {
        when(artistRepository.findPublicArtistDetail(
                "missing", 1, Art.STATUS_ACTIVE, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistQueryService.getArtist("missing"))
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> {
                    DomainApiException domainError = (DomainApiException) error;
                    assertThat(domainError.getStatus().value()).isEqualTo(404);
                    assertThat(domainError.getCode()).isEqualTo("ARTIST_NOT_FOUND");
                });
    }

    @Test
    void artistArtsUseOnlyApprovedPublicStatusesAndPaginationLimit() {
        ArtistDetailResponseDto detail = new ArtistDetailResponseDto(
                "artist-code", "작가", "소개", 0L);
        when(artistRepository.findPublicArtistDetail(
                "artist-code", 1, Art.STATUS_ACTIVE, NOW))
                .thenReturn(Optional.of(detail));
        when(artRepository.findPublicArtsByArtistId(
                eq("artist-code"),
                eq(List.of(Art.STATUS_ACTIVE, Art.STATUS_UNSOLD, Art.STATUS_SOLD)),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ArtResponseDto> result = artistQueryService.getArtistArts(
                "artist-code", -2, 80);

        assertThat(result).isEmpty();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artRepository).findPublicArtsByArtistId(
                eq("artist-code"),
                eq(List.of(Art.STATUS_ACTIVE, Art.STATUS_UNSOLD, Art.STATUS_SOLD)),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void artistArtsRejectUnknownArtistInsteadOfReturningEmptyPage() {
        when(artistRepository.findPublicArtistDetail(
                "missing", 1, Art.STATUS_ACTIVE, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistQueryService.getArtistArts("missing", 0, 12))
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("ARTIST_NOT_FOUND"));
    }

    @Test
    void summaryDtoKeepsFullIntroductionAndDefaultImage() {
        ArtistSummaryResponseDto summary = new ArtistSummaryResponseDto(
                "artist-code", "작가", "말줄임하지 않은 전체 소개", 1L);

        assertThat(summary.getProfileImagePath()).isEqualTo("/img/artist.png");
        assertThat(summary.getArtistIntro()).isEqualTo("말줄임하지 않은 전체 소개");
    }
}
