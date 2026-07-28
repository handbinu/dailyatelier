package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.exception.AuctionCloseIntegrityException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionCloseServiceTest {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-27T09:00:00Z");

    @Mock
    private ArtRepository artRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private OrderService orderService;

    private AuctionCloseService auctionCloseService;

    @BeforeEach
    void setUp() {
        auctionCloseService = new AuctionCloseService(
                artRepository,
                bidRepository,
                orderService,
                Clock.fixed(NOW, ZONE_ID)
        );
    }

    @Test
    void closesAuctionWithoutBidsAsUnsold() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 17, 59));
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));
        when(bidRepository.findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art))
                .thenReturn(Optional.empty());

        AuctionCloseResult result = auctionCloseService.closeAuction(1L);

        assertThat(result).isEqualTo(AuctionCloseResult.UNSOLD);
        assertThat(art.getArtStatus()).isEqualTo(Art.STATUS_UNSOLD);
        assertThat(art.getWinningBid()).isNull();
        assertThat(art.getClosedAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        verify(artRepository).save(art);
        verify(orderService, never()).createForSoldAuction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void closesAuctionWithHighestBidAsSold() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 18, 0));
        art.setCurrentPrice(150_000);
        Bid winningBid = createBid(3L, art, 150_000);
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));
        when(bidRepository.findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art))
                .thenReturn(Optional.of(winningBid));

        AuctionCloseResult result = auctionCloseService.closeAuction(1L);

        assertThat(result).isEqualTo(AuctionCloseResult.SOLD);
        assertThat(art.getArtStatus()).isEqualTo(Art.STATUS_SOLD);
        assertThat(art.getWinningBid()).isSameAs(winningBid);
        assertThat(art.getClosedAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        verify(artRepository).save(art);
        verify(orderService).createForSoldAuction(
                art,
                winningBid,
                LocalDateTime.of(2026, 7, 27, 18, 0)
        );
    }

    @Test
    void doesNotCloseAuctionBeforeClosingTime() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 18, 0, 1));
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));

        AuctionCloseResult result = auctionCloseService.closeAuction(1L);

        assertThat(result).isEqualTo(AuctionCloseResult.NOT_DUE);
        assertThat(art.getArtStatus()).isEqualTo(Art.STATUS_ACTIVE);
        verify(bidRepository, never())
                .findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art);
        verify(artRepository, never()).save(art);
    }

    @Test
    void repeatedCloseDoesNotChangeStoredResult() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 17, 59));
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));
        when(bidRepository.findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art))
                .thenReturn(Optional.empty());

        AuctionCloseResult first = auctionCloseService.closeAuction(1L);
        LocalDateTime firstClosedAt = art.getClosedAt();
        AuctionCloseResult second = auctionCloseService.closeAuction(1L);

        assertThat(first).isEqualTo(AuctionCloseResult.UNSOLD);
        assertThat(second).isEqualTo(AuctionCloseResult.ALREADY_CLOSED);
        assertThat(art.getClosedAt()).isEqualTo(firstClosedAt);
    }

    @Test
    void repeatedCloseOfSoldArtEnsuresMissingOrderIdempotently() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 17, 59));
        art.setArtStatus(Art.STATUS_SOLD);
        art.setClosedAt(LocalDateTime.of(2026, 7, 27, 18, 0));
        Bid winningBid = createBid(3L, art, 150_000);
        art.setWinningBid(winningBid);
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));

        AuctionCloseResult result = auctionCloseService.closeAuction(1L);

        assertThat(result).isEqualTo(AuctionCloseResult.ALREADY_CLOSED);
        verify(orderService).createForSoldAuction(
                art,
                winningBid,
                LocalDateTime.of(2026, 7, 27, 18, 0)
        );
        verify(artRepository, never()).save(art);
    }

    @Test
    void canceledArtDoesNotCreateOrder() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 17, 59));
        art.setArtStatus(Art.STATUS_CANCELED);
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));

        AuctionCloseResult result = auctionCloseService.closeAuction(1L);

        assertThat(result).isEqualTo(AuctionCloseResult.ALREADY_CLOSED);
        verifyNoInteractions(orderService);
        verify(artRepository, never()).save(art);
    }

    @Test
    void priceMismatchLeavesAuctionUnclosed() {
        Art art = createArt(LocalDateTime.of(2026, 7, 27, 17, 59));
        art.setCurrentPrice(160_000);
        Bid winningBid = createBid(3L, art, 150_000);
        when(artRepository.findByIdForClosing(1L)).thenReturn(Optional.of(art));
        when(bidRepository.findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art))
                .thenReturn(Optional.of(winningBid));

        assertThatThrownBy(() -> auctionCloseService.closeAuction(1L))
                .isInstanceOf(AuctionCloseIntegrityException.class)
                .hasMessageContaining("artId=1")
                .hasMessageContaining("currentPrice=160000")
                .hasMessageContaining("winningPrice=150000");

        assertThat(art.getArtStatus()).isEqualTo(Art.STATUS_ACTIVE);
        assertThat(art.getWinningBid()).isNull();
        assertThat(art.getClosedAt()).isNull();
        verify(artRepository, never()).save(art);
    }

    private Art createArt(LocalDateTime closingTime) {
        Art art = new Art();
        art.setArtId(1L);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setClosingTime(closingTime);
        art.setArtStatus(Art.STATUS_ACTIVE);
        return art;
    }

    private Bid createBid(Long bidId, Art art, int bidPrice) {
        Bid bid = new Bid();
        bid.setBidId(bidId);
        bid.setArt(art);
        bid.setBidPrice(bidPrice);
        bid.setBidTime(LocalDateTime.of(2026, 7, 27, 17, 30));
        return bid;
    }
}
