package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtUpdateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ArtServiceMutationTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 15, 0);

    @Mock
    private ArtRepository artRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private LikesRepository likesRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointAccountRepository pointAccountRepository;

    @Mock
    private PointHoldRepository pointHoldRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    private ArtService artService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T06:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        artService = new ArtService(
                artRepository,
                artistRepository,
                bidRepository,
                likesRepository,
                reviewRepository,
                userRepository,
                pointAccountRepository,
                pointHoldRepository,
                pointTransactionRepository,
                clock
        );
        lenient().when(artRepository.save(any(Art.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesPricePeriodAndNonPriceFieldsBeforeFirstBid() {
        Art art = createActiveArt("owner");
        ArtUpdateRequestDto request = new ArtUpdateRequestDto();
        request.setStartPrice(130_000);
        request.setBidStartTime(NOW.plusHours(1));
        request.setClosingTime(NOW.plusDays(2));
        request.setDescript("  변경 설명  ");
        request.setMaterial("  종이  ");
        request.setWIntro("  소개  ");
        request.setImgPath("  https://example.com/changed.jpg  ");
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(false);

        ArtResponseDto response = artService.updateArt(1L, "owner", request);

        assertThat(response.getStartPrice()).isEqualTo(130_000);
        assertThat(response.getCurrentPrice()).isEqualTo(130_000);
        assertThat(response.getBidStartTime()).isEqualTo(NOW.plusHours(1));
        assertThat(response.getClosingTime()).isEqualTo(NOW.plusDays(2));
        assertThat(response.getDescript()).isEqualTo("변경 설명");
        assertThat(response.getMaterial()).isEqualTo("종이");
        assertThat(response.getWIntro()).isEqualTo("소개");
        assertThat(response.getImgPath())
                .isEqualTo("https://example.com/changed.jpg");
    }

    @Test
    void updatesOnlyNonPriceFieldsAfterBid() {
        Art art = createActiveArt("owner");
        ArtUpdateRequestDto request = new ArtUpdateRequestDto();
        request.setDescript(null);
        request.setImgPath("https://example.com/changed.jpg");
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(true);

        ArtResponseDto response = artService.updateArt(1L, "owner", request);

        assertThat(response.getDescript()).isNull();
        assertThat(response.getImgPath())
                .isEqualTo("https://example.com/changed.jpg");
        assertThat(response.getStartPrice()).isEqualTo(100_000);
        assertThat(response.getCurrentPrice()).isEqualTo(120_000);
    }

    @Test
    void rejectsPriceOrPeriodFieldsAfterBid() {
        Art art = createActiveArt("owner");
        ArtUpdateRequestDto request = new ArtUpdateRequestDto();
        request.setStartPrice(130_000);
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(true);

        assertStatus(
                () -> artService.updateArt(1L, "owner", request),
                HttpStatus.CONFLICT
        );
        verify(artRepository, never()).save(art);
    }

    @Test
    void rejectsNonOwnerAndNonArtistOwner() {
        Art art = createActiveArt("owner");
        ArtUpdateRequestDto request = descriptionRequest();
        stubLockedArt(art);

        assertStatus(
                () -> artService.updateArt(1L, "other", request),
                HttpStatus.FORBIDDEN
        );

        art.getArtist().getUser().setUserStatus(0);
        assertStatus(
                () -> artService.updateArt(1L, "owner", request),
                HttpStatus.FORBIDDEN
        );
        verify(bidRepository, never()).existsByArt(any());
    }

    @Test
    void rejectsEndedCanceledAndExpiredArt() {
        ArtUpdateRequestDto request = descriptionRequest();
        for (int status : new int[]{
                Art.STATUS_UNSOLD,
                Art.STATUS_SOLD,
                Art.STATUS_CANCELED
        }) {
            Art art = createActiveArt("owner");
            art.setArtStatus(status);
            stubLockedArt(art);
            assertStatus(
                    () -> artService.updateArt(1L, "owner", request),
                    HttpStatus.CONFLICT
            );
        }

        Art expired = createActiveArt("owner");
        expired.setClosingTime(NOW);
        stubLockedArt(expired);
        assertStatus(
                () -> artService.updateArt(1L, "owner", request),
                HttpStatus.CONFLICT
        );
    }

    @Test
    void rejectsInvalidUpdatedPeriod() {
        Art art = createActiveArt("owner");
        ArtUpdateRequestDto request = new ArtUpdateRequestDto();
        request.setClosingTime(art.getBidStartTime());
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(false);

        assertStatus(
                () -> artService.updateArt(1L, "owner", request),
                HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void physicallyDeletesArtWithoutBidAndDetachesLikes() {
        Art art = createActiveArt("owner");
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(false);
        when(reviewRepository.existsByArt(art)).thenReturn(false);

        ArtDeleteResponseDto response =
                artService.deleteArt(1L, "owner");

        assertThat(response.getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.DELETED);
        assertThat(response.getArtStatus()).isNull();
        verify(likesRepository).detachArt(art);
        verify(artRepository).delete(art);
    }

    @Test
    void changesArtWithBidToCanceledAndPreservesRelations() {
        Art art = createActiveArt("owner");
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(true);

        ArtDeleteResponseDto response =
                artService.deleteArt(1L, "owner");

        assertThat(response.getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.CANCELED);
        assertThat(response.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
        assertThat(art.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
        assertThat(art.getClosedAt()).isEqualTo(NOW);
        verify(likesRepository, never()).detachArt(any());
        verify(artRepository, never()).delete(any());
    }

    @Test
    void rejectsPhysicalDeleteWhenReviewExists() {
        Art art = createActiveArt("owner");
        stubLockedArt(art);
        when(bidRepository.existsByArt(art)).thenReturn(false);
        when(reviewRepository.existsByArt(art)).thenReturn(true);

        assertStatus(
                () -> artService.deleteArt(1L, "owner"),
                HttpStatus.CONFLICT
        );
        verify(likesRepository, never()).detachArt(any());
        verify(artRepository, never()).delete(any());
    }

    @Test
    void rejectsDeleteForEndedAndExpiredArt() {
        Art ended = createActiveArt("owner");
        ended.setArtStatus(Art.STATUS_SOLD);
        stubLockedArt(ended);
        assertStatus(
                () -> artService.deleteArt(1L, "owner"),
                HttpStatus.CONFLICT
        );

        Art expired = createActiveArt("owner");
        expired.setClosingTime(NOW);
        stubLockedArt(expired);
        assertStatus(
                () -> artService.deleteArt(1L, "owner"),
                HttpStatus.CONFLICT
        );
        verify(bidRepository, never()).existsByArt(any());
    }

    @Test
    void reportsMissingArtAndLockConflict() {
        when(artRepository.findByIdForUpdate(404L))
                .thenReturn(Optional.empty());
        assertStatus(
                () -> artService.deleteArt(404L, "owner"),
                HttpStatus.NOT_FOUND
        );

        when(artRepository.findByIdForUpdate(1L))
                .thenThrow(new PessimisticLockingFailureException("timeout"));
        assertStatus(
                () -> artService.deleteArt(1L, "owner"),
                HttpStatus.CONFLICT
        );
    }

    private ArtUpdateRequestDto descriptionRequest() {
        ArtUpdateRequestDto request = new ArtUpdateRequestDto();
        request.setDescript("변경 설명");
        return request;
    }

    private void stubLockedArt(Art art) {
        when(artRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(art));
    }

    private Art createActiveArt(String ownerUserId) {
        User owner = new User();
        owner.setUserId(ownerUserId);
        owner.setUserStatus(1);

        Artist artist = new Artist();
        artist.setArtistCode("artist-code");
        artist.setArtistName("테스트 작가");
        artist.setUser(owner);

        Art art = new Art();
        art.setArtId(1L);
        art.setArtist(artist);
        art.setName("테스트 작품");
        art.setDescript("기존 설명");
        art.setMaterial("캔버스");
        art.setWIntro("기존 소개");
        art.setStartPrice(100_000);
        art.setCurrentPrice(120_000);
        art.setBidStartTime(NOW.minusDays(1));
        art.setClosingTime(NOW.plusDays(1));
        art.setImgPath("https://example.com/original.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        return art;
    }

    private void assertStatus(
            ThrowingCallable callable,
            HttpStatus expectedStatus) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(
                        ((ResponseStatusException) error).getStatusCode()
                ).isEqualTo(expectedStatus));
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
