package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.exception.AuctionCloseIntegrityException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionCloseService {

    private final ArtRepository artRepository;
    private final BidRepository bidRepository;
    private final OrderService orderService;
    private final Clock clock;

    @Transactional
    public AuctionCloseResult closeAuction(Long artId) {
        Art art = artRepository.findByIdForClosing(artId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "작품을 찾을 수 없습니다. artId=" + artId));

        if (art.getArtStatus() == null || art.getArtStatus() != Art.STATUS_ACTIVE) {
            if (art.getArtStatus() != null
                    && art.getArtStatus() == Art.STATUS_SOLD
                    && art.getWinningBid() != null) {
                LocalDateTime orderCreatedAt = art.getClosedAt() == null
                        ? LocalDateTime.now(clock)
                        : art.getClosedAt();
                orderService.createForSoldAuction(
                        art,
                        art.getWinningBid(),
                        orderCreatedAt
                );
            }
            return AuctionCloseResult.ALREADY_CLOSED;
        }

        LocalDateTime closedAt = LocalDateTime.now(clock);
        if (closedAt.isBefore(art.getClosingTime())) {
            return AuctionCloseResult.NOT_DUE;
        }

        Optional<Bid> winningBid = bidRepository
                .findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(art);

        if (winningBid.isEmpty()) {
            art.setWinningBid(null);
            art.setArtStatus(Art.STATUS_UNSOLD);
            art.setClosedAt(closedAt);
            artRepository.save(art);
            return AuctionCloseResult.UNSOLD;
        }

        Bid winner = winningBid.get();
        if (!winner.getBidPrice().equals(art.getCurrentPrice())) {
            log.error(
                    "경매 마감 가격 불일치: artId={}, currentPrice={}, winningPrice={}",
                    art.getArtId(),
                    art.getCurrentPrice(),
                    winner.getBidPrice()
            );
            throw new AuctionCloseIntegrityException(
                    art.getArtId(),
                    art.getCurrentPrice(),
                    winner.getBidPrice()
            );
        }

        art.setWinningBid(winner);
        art.setArtStatus(Art.STATUS_SOLD);
        art.setClosedAt(closedAt);
        artRepository.save(art);
        orderService.createForSoldAuction(art, winner, closedAt);
        return AuctionCloseResult.SOLD;
    }
}
