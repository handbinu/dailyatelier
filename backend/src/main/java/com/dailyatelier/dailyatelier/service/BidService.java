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
import jakarta.transaction.Transactional;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BidService {
    private static final int MIN_BID_PRICE = 1;
    private static final int MAX_BID_PRICE = 2_100_000_000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final long IMMINENT_HOURS = 24;

    private final ArtRepository artRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public BidCreateResponseDto createBid(
            Long artId,
            String userId,
            BidCreateRequestDto request) {
        Art art = findArtForUpdate(artId);
        User bidder = userRepository.findByUserId(userId);
        if (bidder == null) {
            throw new BidApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "인증된 사용자를 찾을 수 없습니다."
            );
        }

        validateBidderIsNotSeller(art, userId);
        LocalDateTime bidTime = LocalDateTime.now(clock);
        validateAuctionIsOpen(art, bidTime);
        validateBidPrice(request == null ? null : request.getBidPrice(), art.getCurrentPrice());

        Integer bidPrice = request.getBidPrice();

        art.setCurrentPrice(bidPrice);
        artRepository.save(art);

        Bid bid = new Bid();
        bid.setUser(bidder);
        bid.setArt(art);
        bid.setBidPrice(bidPrice);
        bid.setBidTime(bidTime);
        Bid savedBid = bidRepository.save(bid);

        return new BidCreateResponseDto(
                savedBid.getBidId(),
                art.getArtId(),
                savedBid.getBidPrice(),
                art.getCurrentPrice(),
                savedBid.getBidTime()
        );
    }

    public Page<BidStatusResponseDto> getMyBids(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
        LocalDateTime now = LocalDateTime.now(clock);
        return bidRepository.findBidSummariesByUserId(userId, pageable)
                .map(summary -> toBidStatusResponse(summary, userId, now));
    }

    private Art findArtForUpdate(Long artId) {
        try {
            return artRepository.findByIdForUpdate(artId)
                    .orElseThrow(() -> new BidApiException(
                            HttpStatus.NOT_FOUND,
                            "ART_NOT_FOUND",
                            "작품을 찾을 수 없습니다."
                    ));
        } catch (LockTimeoutException
                 | PessimisticLockException
                 | PessimisticLockingFailureException
                 | QueryTimeoutException exception) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "BID_CONFLICT",
                    "다른 입찰이 처리 중입니다. 잠시 후 다시 시도해 주세요."
            );
        }
    }

    private void validateBidderIsNotSeller(Art art, String userId) {
        Artist artist = art.getArtist();
        User seller = artist == null ? null : artist.getUser();
        if (seller != null && userId.equals(seller.getUserId())) {
            throw new BidApiException(
                    HttpStatus.FORBIDDEN,
                    "SELF_BID_NOT_ALLOWED",
                    "본인 작품에는 입찰할 수 없습니다."
            );
        }
    }

    private void validateAuctionIsOpen(Art art, LocalDateTime now) {
        if (art.getArtStatus() == null || art.getArtStatus() != Art.STATUS_ACTIVE) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "AUCTION_CLOSED",
                    "진행 중인 경매가 아닙니다."
            );
        }

        if (now.isBefore(art.getBidStartTime())) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "AUCTION_NOT_STARTED",
                    "아직 경매가 시작되지 않았습니다."
            );
        }
        if (!now.isBefore(art.getClosingTime())) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "AUCTION_CLOSED",
                    "마감된 경매입니다."
            );
        }
    }

    private void validateBidPrice(Integer bidPrice, Integer currentPrice) {
        if (bidPrice == null || bidPrice < MIN_BID_PRICE || bidPrice > MAX_BID_PRICE) {
            throw new BidApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_BID_AMOUNT",
                    "입찰 금액은 1원 이상 21억 원 이하의 정수여야 합니다."
            );
        }
        if (currentPrice == null || bidPrice <= currentPrice) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "BID_TOO_LOW",
                    "입찰 금액은 현재가보다 높아야 합니다."
            );
        }
    }

    private BidStatusResponseDto toBidStatusResponse(
            BidSummaryQueryDto summary,
            String userId,
            LocalDateTime now) {
        boolean isLeading = summary.getMyBidPrice().equals(summary.getCurrentPrice());
        return new BidStatusResponseDto(
                summary.getArtId(),
                summary.getArtName(),
                summary.getArtistName(),
                summary.getImgPath(),
                summary.getMyBidPrice(),
                summary.getCurrentPrice(),
                isLeading,
                resolveAuctionStatus(summary, now),
                resolveBidResult(summary, userId),
                resolveBidResultMessage(summary),
                summary.getLastBidTime(),
                summary.getBidStartTime(),
                summary.getClosingTime()
        );
    }

    private String resolveAuctionStatus(BidSummaryQueryDto summary, LocalDateTime now) {
        if (summary.getArtStatus() == null
                || summary.getArtStatus() != Art.STATUS_ACTIVE) {
            return "ENDED";
        }
        if (!summary.getClosingTime().isAfter(now.plusHours(IMMINENT_HOURS))) {
            return "IMMINENT";
        }
        return "ONGOING";
    }

    private String resolveBidResult(BidSummaryQueryDto summary, String userId) {
        if (summary.getArtStatus() != null
                && summary.getArtStatus() == Art.STATUS_ACTIVE) {
            return "PENDING";
        }
        if (summary.getArtStatus() != null
                && summary.getArtStatus() == Art.STATUS_CANCELED) {
            return "CANCELED";
        }
        if (summary.getArtStatus() != null
                && summary.getArtStatus() == Art.STATUS_SOLD
                && userId.equals(summary.getWinningUserId())) {
            return "WON";
        }
        return "LOST";
    }

    private String resolveBidResultMessage(BidSummaryQueryDto summary) {
        if (summary.getArtStatus() != null
                && summary.getArtStatus() == Art.STATUS_CANCELED) {
            return "작가가 취소한 경매입니다.";
        }
        return null;
    }
}
