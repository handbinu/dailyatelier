package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.dto.BidStatusResponseDto;
import com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.entity.PointHoldReleaseReason;
import com.dailyatelier.dailyatelier.entity.PointReferenceType;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final PointAccountRepository pointAccountRepository;
    private final PointHoldRepository pointHoldRepository;
    private final PointTransactionRepository pointTransactionRepository;
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
        PointHold activeHold = art.getActivePointHold();
        Map<String, PointAccount> accounts = lockAccounts(
                bidder.getUserId(),
                activeHold == null ? null : activeHold.getUser().getUserId()
        );

        Bid bid = new Bid();
        bid.setUser(bidder);
        bid.setArt(art);
        bid.setBidPrice(bidPrice);
        bid.setBidTime(bidTime);
        Bid savedBid = bidRepository.save(bid);

        applyPointHold(art, bidder, savedBid, activeHold, accounts, bidTime);
        art.setCurrentPrice(bidPrice);
        artRepository.save(art);

        return new BidCreateResponseDto(
                savedBid.getBidId(),
                art.getArtId(),
                savedBid.getBidPrice(),
                art.getCurrentPrice(),
                savedBid.getBidTime(),
                accounts.get(bidder.getUserId()).getAvailableBalance(),
                accounts.get(bidder.getUserId()).getHeldBalance()
        );
    }

    private Map<String, PointAccount> lockAccounts(String bidderId, String previousBidderId) {
        List<String> userIds = java.util.stream.Stream.of(bidderId, previousBidderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<String, PointAccount> accounts = new LinkedHashMap<>();
        for (String accountUserId : userIds) {
            PointAccount account = pointAccountRepository.findByUserIdForUpdate(accountUserId)
                    .orElseThrow(() -> new BidApiException(
                            HttpStatus.CONFLICT,
                            "POINT_ACCOUNT_NOT_FOUND",
                            "포인트 계정을 찾을 수 없습니다."
                    ));
            accounts.put(accountUserId, account);
        }
        return accounts;
    }

    private void applyPointHold(
            Art art,
            User bidder,
            Bid savedBid,
            PointHold activeHold,
            Map<String, PointAccount> accounts,
            LocalDateTime now) {
        long bidAmount = savedBid.getBidPrice().longValue();
        PointAccount bidderAccount = accounts.get(bidder.getUserId());
        boolean sameBidder = activeHold != null
                && bidder.getUserId().equals(activeHold.getUser().getUserId());
        long holdAmount = sameBidder ? bidAmount - activeHold.getAmount() : bidAmount;
        if (bidderAccount.getAvailableBalance() < holdAmount) {
            throw new BidApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_POINTS",
                    "사용 가능한 포인트가 부족합니다."
            );
        }

        bidderAccount.hold(holdAmount, now);
        pointAccountRepository.save(bidderAccount);

        PointHold nextHold;
        PointTransactionType holdType;
        if (sameBidder) {
            activeHold.increase(savedBid, holdAmount, now);
            nextHold = pointHoldRepository.save(activeHold);
            holdType = PointTransactionType.HOLD_INCREASE;
        } else {
            nextHold = pointHoldRepository.save(
                    PointHold.hold(art, bidder, savedBid, bidAmount, now)
            );
            holdType = PointTransactionType.HOLD;
        }
        pointTransactionRepository.save(PointTransaction.record(
                bidder.getUserId(),
                holdType,
                holdAmount,
                -holdAmount,
                holdAmount,
                bidderAccount.getAvailableBalance(),
                bidderAccount.getHeldBalance(),
                PointReferenceType.BID,
                String.valueOf(savedBid.getBidId()),
                "bid:" + savedBid.getBidId() + ":" + holdType,
                null,
                holdType.name(),
                sameBidder ? "최고 입찰 차액 예치" : "최고 입찰 전액 예치",
                now
        ));

        if (activeHold != null && !sameBidder) {
            PointAccount previousAccount = accounts.get(activeHold.getUser().getUserId());
            long releaseAmount = activeHold.getAmount();
            previousAccount.release(releaseAmount, now);
            activeHold.release(PointHoldReleaseReason.OUTBID, now);
            pointAccountRepository.save(previousAccount);
            pointHoldRepository.save(activeHold);
            pointTransactionRepository.save(PointTransaction.record(
                    previousAccount.getUserId(),
                    PointTransactionType.RELEASE,
                    releaseAmount,
                    releaseAmount,
                    -releaseAmount,
                    previousAccount.getAvailableBalance(),
                    previousAccount.getHeldBalance(),
                    PointReferenceType.HOLD,
                    String.valueOf(activeHold.getHoldId()),
                    "hold:" + activeHold.getHoldId() + ":release:outbid",
                    null,
                    PointHoldReleaseReason.OUTBID.name(),
                    "패찰 예치 해제",
                    now
            ));
        }
        art.setActivePointHold(nextHold);
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
