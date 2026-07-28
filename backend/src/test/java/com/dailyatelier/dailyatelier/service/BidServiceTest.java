package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.dto.BidStatusResponseDto;
import com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 18, 0);

    @Mock
    private ArtRepository artRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    private BidService bidService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T09:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        bidService = new BidService(artRepository, bidRepository, userRepository, clock);
    }

    @Test
    void createBidSavesBidAndUpdatesCurrentPrice() {
        Art art = createOpenArt("seller", 100_000);
        User bidder = createUser("bidder");
        BidCreateRequestDto request = createRequest(120_000);
        when(artRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(art));
        when(userRepository.findByUserId("bidder")).thenReturn(bidder);
        when(artRepository.save(art)).thenReturn(art);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setBidId(31L);
            return bid;
        });

        BidCreateResponseDto response = bidService.createBid(1L, "bidder", request);

        assertThat(response.getBidId()).isEqualTo(31L);
        assertThat(response.getArtId()).isEqualTo(1L);
        assertThat(response.getBidPrice()).isEqualTo(120_000);
        assertThat(response.getCurrentPrice()).isEqualTo(120_000);
        assertThat(response.getBidTime()).isEqualTo(NOW);
        assertThat(art.getCurrentPrice()).isEqualTo(120_000);
        verify(artRepository).save(art);
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    void createBidRejectsMissingArt() {
        when(artRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertBidError(
                () -> bidService.createBid(404L, "bidder", createRequest(120_000)),
                HttpStatus.NOT_FOUND,
                "ART_NOT_FOUND"
        );
        verify(bidRepository, never()).save(any());
    }

    @Test
    void createBidReturnsConflictWhenPessimisticLockFails() {
        when(artRepository.findByIdForUpdate(1L))
                .thenThrow(new PessimisticLockingFailureException("lock timeout"));

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(120_000)),
                HttpStatus.CONFLICT,
                "BID_CONFLICT"
        );
        verify(bidRepository, never()).save(any());
    }

    @Test
    void createBidRejectsSellerAndLeavesPriceUnchanged() {
        Art art = createOpenArt("seller", 100_000);
        when(artRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(art));
        when(userRepository.findByUserId("seller")).thenReturn(createUser("seller"));

        assertBidError(
                () -> bidService.createBid(1L, "seller", createRequest(120_000)),
                HttpStatus.FORBIDDEN,
                "SELF_BID_NOT_ALLOWED"
        );
        assertThat(art.getCurrentPrice()).isEqualTo(100_000);
        verify(artRepository, never()).save(any());
        verify(bidRepository, never()).save(any());
    }

    @Test
    void createBidRejectsAuctionBeforeStart() {
        Art art = createOpenArt("seller", 100_000);
        art.setBidStartTime(NOW.plusSeconds(1));
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(120_000)),
                HttpStatus.CONFLICT,
                "AUCTION_NOT_STARTED"
        );
    }

    @Test
    void createBidAllowsExactStartBoundary() {
        Art art = createOpenArt("seller", 100_000);
        art.setBidStartTime(NOW);
        stubBidderAndArt(art);
        when(artRepository.save(art)).thenReturn(art);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BidCreateResponseDto response =
                bidService.createBid(1L, "bidder", createRequest(120_000));

        assertThat(response.getCurrentPrice()).isEqualTo(120_000);
    }

    @Test
    void createBidRejectsExactClosingBoundary() {
        Art art = createOpenArt("seller", 100_000);
        art.setClosingTime(NOW);
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(120_000)),
                HttpStatus.CONFLICT,
                "AUCTION_CLOSED"
        );
    }

    @Test
    void createBidRejectsInactiveArt() {
        Art art = createOpenArt("seller", 100_000);
        art.setArtStatus(1);
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(120_000)),
                HttpStatus.CONFLICT,
                "AUCTION_CLOSED"
        );
    }

    @Test
    void createBidRejectsCanceledArt() {
        Art art = createOpenArt("seller", 100_000);
        art.setArtStatus(Art.STATUS_CANCELED);
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(120_000)),
                HttpStatus.CONFLICT,
                "AUCTION_CLOSED"
        );
    }

    @Test
    void createBidRejectsEqualOrLowerPrice() {
        Art art = createOpenArt("seller", 100_000);
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(100_000)),
                HttpStatus.CONFLICT,
                "BID_TOO_LOW"
        );
        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(99_999)),
                HttpStatus.CONFLICT,
                "BID_TOO_LOW"
        );
    }

    @Test
    void createBidRejectsAmountOutsideIntegerPolicy() {
        Art art = createOpenArt("seller", 100_000);
        stubBidderAndArt(art);

        assertBidError(
                () -> bidService.createBid(1L, "bidder", createRequest(0)),
                HttpStatus.BAD_REQUEST,
                "INVALID_BID_AMOUNT"
        );
        assertBidError(
                () -> bidService.createBid(1L, "bidder", null),
                HttpStatus.BAD_REQUEST,
                "INVALID_BID_AMOUNT"
        );
    }

    @Test
    void getMyBidsMapsLeadingAndAuctionStatuses() {
        List<BidSummaryQueryDto> summaries = List.of(
                createSummary(1L, 120_000, 120_000, NOW.plusHours(25), 0),
                createSummary(2L, 110_000, 120_000, NOW.plusHours(24), 0),
                createSummary(3L, 130_000, 130_000, NOW.plusDays(1), 1),
                createSummary(4L, 140_000, 140_000, NOW, 0)
        );
        when(bidRepository.findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(summaries));

        Page<BidStatusResponseDto> result = bidService.getMyBids("bidder", 0, 12);

        assertThat(result.getContent())
                .extracting(BidStatusResponseDto::getAuctionStatus)
                .containsExactly("ONGOING", "IMMINENT", "ENDED", "IMMINENT");
        assertThat(result.getContent())
                .extracting(BidStatusResponseDto::isLeading)
                .containsExactly(true, false, true, true);
    }

    @Test
    void getMyBidsMapsPendingWonAndLostResults() {
        List<BidSummaryQueryDto> summaries = List.of(
                createSummary(1L, 120_000, 120_000, NOW.plusDays(1), 0, null),
                createSummary(2L, 130_000, 130_000, NOW.minusDays(1), 2, "bidder"),
                createSummary(3L, 130_000, 150_000, NOW.minusDays(1), 2, "other"),
                createSummary(4L, 110_000, 110_000, NOW.minusDays(1), 1, null)
        );
        when(bidRepository.findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(summaries));

        Page<BidStatusResponseDto> result = bidService.getMyBids("bidder", 0, 12);

        assertThat(result.getContent())
                .extracting(BidStatusResponseDto::getBidResult)
                .containsExactly("PENDING", "WON", "LOST", "LOST");
    }

    @Test
    void getMyBidsMapsCanceledResultAndGuideMessage() {
        BidSummaryQueryDto canceled = createSummary(
                5L,
                120_000,
                120_000,
                NOW.plusDays(1),
                Art.STATUS_CANCELED,
                null
        );
        when(bidRepository.findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(canceled)));

        BidStatusResponseDto result =
                bidService.getMyBids("bidder", 0, 12).getContent().get(0);

        assertThat(result.getAuctionStatus()).isEqualTo("ENDED");
        assertThat(result.getBidResult()).isEqualTo("CANCELED");
        assertThat(result.getBidResultMessage())
                .isEqualTo("작가가 취소한 경매입니다.");
    }

    @Test
    void getMyBidsNormalizesPageAndCapsPageSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(bidRepository.findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        bidService.getMyBids("bidder", -3, 100);

        verify(bidRepository).findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getMyBidsReturnsEmptyPage() {
        when(bidRepository.findBidSummariesByUserId(
                org.mockito.ArgumentMatchers.eq("bidder"),
                any(Pageable.class)
        )).thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        Page<BidStatusResponseDto> result = bidService.getMyBids("bidder", 2, 12);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getContent()).isEmpty();
    }

    private void stubBidderAndArt(Art art) {
        when(artRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(art));
        when(userRepository.findByUserId("bidder")).thenReturn(createUser("bidder"));
    }

    private Art createOpenArt(String sellerId, int currentPrice) {
        Art art = new Art();
        art.setArtId(1L);
        art.setArtist(createArtist(createUser(sellerId)));
        art.setName("테스트 작품");
        art.setStartPrice(90_000);
        art.setCurrentPrice(currentPrice);
        art.setBidStartTime(NOW.minusDays(1));
        art.setClosingTime(NOW.plusDays(1));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(0);
        return art;
    }

    private Artist createArtist(User user) {
        Artist artist = new Artist();
        artist.setArtistCode("artist-code");
        artist.setArtistName("테스트 작가");
        artist.setUser(user);
        return artist;
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }

    private BidCreateRequestDto createRequest(int bidPrice) {
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(bidPrice);
        return request;
    }

    private BidSummaryQueryDto createSummary(
            Long artId,
            int myBidPrice,
            int currentPrice,
            LocalDateTime closingTime,
            int artStatus) {
        return createSummary(
                artId,
                myBidPrice,
                currentPrice,
                closingTime,
                artStatus,
                null
        );
    }

    private BidSummaryQueryDto createSummary(
            Long artId,
            int myBidPrice,
            int currentPrice,
            LocalDateTime closingTime,
            int artStatus,
            String winningUserId) {
        return new BidSummaryQueryDto(
                artId,
                "테스트 작품 " + artId,
                "테스트 작가",
                "https://example.com/art.jpg",
                myBidPrice,
                currentPrice,
                NOW.minusHours(1),
                NOW.minusDays(1),
                closingTime,
                artStatus,
                winningUserId
        );
    }

    private void assertBidError(
            ThrowingCallable callable,
            HttpStatus expectedStatus,
            String expectedCode) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BidApiException.class)
                .satisfies(error -> {
                    BidApiException exception = (BidApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(expectedStatus);
                    assertThat(exception.getCode()).isEqualTo(expectedCode);
                });
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
