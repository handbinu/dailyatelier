package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtUpdateRequestDto;
import com.dailyatelier.dailyatelier.dto.MyArtQueryDto;
import com.dailyatelier.dailyatelier.dto.MyArtResponseDto;
import com.dailyatelier.dailyatelier.dto.MyArtState;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.entity.PointHoldReleaseReason;
import com.dailyatelier.dailyatelier.entity.PointReferenceType;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArtService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ArtRepository artRepository;
    private final ArtistRepository artistRepository;
    private final BidRepository bidRepository;
    private final LikesRepository likesRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointHoldRepository pointHoldRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final Clock clock;

    public Page<ArtResponseDto> getActiveArts(int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        return artRepository.findByArtStatus(Art.STATUS_ACTIVE, pageable)
                .map(this::toResponse);
    }

    public ArtDetailResponseDto getArt(Long artId, String userId) {
        Art art = artRepository.findById(artId)
                .orElseThrow(this::artNotFound);
        Artist artist = art.getArtist();
        boolean isOwner = userId != null
                && artist.getUser() != null
                && userId.equals(artist.getUser().getUserId());

        return new ArtDetailResponseDto(
                art.getArtId(),
                artist.getArtistCode(),
                artist.getArtistName(),
                art.getName(),
                art.getDescript(),
                art.getMaterial(),
                art.getWIntro(),
                art.getStartPrice(),
                art.getCurrentPrice(),
                art.getBidStartTime(),
                art.getClosingTime(),
                art.getImgPath(),
                art.getArtStatus(),
                isOwner
        );
    }

    public Page<MyArtResponseDto> getMyArts(
            String userId,
            MyArtState state,
            int page,
            int size) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new DomainApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new DomainApiException(
                    HttpStatus.FORBIDDEN,
                    "ARTIST_ROLE_REQUIRED",
                    "Only artist users can view their arts"
            );
        }

        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new DomainApiException(
                        HttpStatus.FORBIDDEN,
                        "ARTIST_PROFILE_NOT_FOUND",
                        "Artist profile not found"
                ));
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
        return artRepository.findMyArtSummaries(
                        artist.getArtistCode(),
                        state.getArtStatuses(),
                        pageable
                )
                .map(this::toMyArtResponse);
    }

    @Transactional
    public ArtResponseDto createArt(String userId, ArtCreateRequestDto dto) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new DomainApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new DomainApiException(
                    HttpStatus.FORBIDDEN,
                    "ARTIST_ROLE_REQUIRED",
                    "Only artist users can create art"
            );
        }

        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new DomainApiException(
                        HttpStatus.FORBIDDEN,
                        "ARTIST_PROFILE_NOT_FOUND",
                        "Artist profile not found"
                ));

        if (dto.getClosingTime().isBefore(dto.getBidStartTime()) || dto.getClosingTime().isEqual(dto.getBidStartTime())) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AUCTION_PERIOD",
                    "Closing time must be after bid start time"
            );
        }
        validateClassification(dto.getFormat(), dto.getCategory());

        Art art = new Art();
        art.setArtist(artist);
        art.setName(dto.getName().trim());
        art.setDescript(blankToNull(dto.getDescript()));
        art.setMaterial(blankToNull(dto.getMaterial()));
        art.setFormat(dto.getFormat());
        art.setCategory(dto.getCategory());
        art.setWIntro(blankToNull(dto.getWIntro()));
        art.setStartPrice(dto.getStartPrice());
        art.setCurrentPrice(dto.getStartPrice());
        art.setBidStartTime(dto.getBidStartTime());
        art.setClosingTime(dto.getClosingTime());
        art.setImgPath(dto.getImgPath().trim());
        art.setArtStatus(Art.STATUS_ACTIVE);
        art.setCreatedAt(LocalDateTime.now(clock));

        return toResponse(artRepository.save(art));
    }

    @Transactional
    public ArtResponseDto updateArt(
            Long artId,
            String userId,
            ArtUpdateRequestDto dto) {
        Art art = findArtForMutation(artId);
        LocalDateTime now = LocalDateTime.now(clock);
        validateMutationAccess(art, userId, now);

        boolean hasBid = bidRepository.existsByArt(art);
        if (hasBid && hasPriceOrPeriodChange(dto)) {
            throw new DomainApiException(
                    HttpStatus.CONFLICT,
                    "ART_BID_RESTRICTION",
                    "입찰 후에는 가격과 기간을 수정할 수 없습니다."
            );
        }

        LocalDateTime bidStartTime = dto.isBidStartTimeProvided()
                ? dto.getBidStartTime()
                : art.getBidStartTime();
        LocalDateTime closingTime = dto.isClosingTimeProvided()
                ? dto.getClosingTime()
                : art.getClosingTime();
        validateAuctionPeriod(bidStartTime, closingTime, now);
        ArtFormat format = dto.isFormatProvided() ? dto.getFormat() : art.getFormat();
        ArtCategory category = dto.isCategoryProvided() ? dto.getCategory() : art.getCategory();
        validateClassification(format, category);

        applyUpdate(art, dto);
        return toResponse(artRepository.save(art));
    }

    @Transactional
    public ArtDeleteResponseDto deleteArt(Long artId, String userId) {
        Art art = findArtForMutation(artId);
        LocalDateTime now = LocalDateTime.now(clock);
        validateMutationAccess(art, userId, now);

        if (bidRepository.existsByArt(art)) {
            releaseActiveHoldForCancellation(art, now);
            art.setArtStatus(Art.STATUS_CANCELED);
            art.setClosedAt(now);
            artRepository.save(art);
            return new ArtDeleteResponseDto(
                    art.getArtId(),
                    ArtDeleteResponseDto.Action.CANCELED,
                    Art.STATUS_CANCELED
            );
        }

        if (reviewRepository.existsByArt(art)) {
            throw new DomainApiException(
                    HttpStatus.CONFLICT,
                    "ART_HAS_REVIEW",
                    "리뷰가 있는 작품은 삭제할 수 없습니다."
            );
        }

        likesRepository.detachArt(art);
        artRepository.delete(art);
        return new ArtDeleteResponseDto(
                artId,
                ArtDeleteResponseDto.Action.DELETED,
                null
        );
    }

    private void releaseActiveHoldForCancellation(Art art, LocalDateTime now) {
        PointHold linkedHold = art.getActivePointHold();
        if (linkedHold == null) {
            return;
        }
        PointHold hold = pointHoldRepository.findByIdForUpdate(linkedHold.getHoldId())
                .orElseThrow(() -> new DomainApiException(
                        HttpStatus.CONFLICT,
                        "ACTIVE_POINT_HOLD_NOT_FOUND",
                        "활성 포인트 예치를 찾을 수 없습니다."
                ));
        PointAccount account = pointAccountRepository
                .findByUserIdForUpdate(hold.getUser().getUserId())
                .orElseThrow(() -> new DomainApiException(
                        HttpStatus.CONFLICT,
                        "POINT_ACCOUNT_NOT_FOUND",
                        "포인트 계정을 찾을 수 없습니다."
                ));
        long amount = hold.getAmount();
        account.release(amount, now);
        hold.release(PointHoldReleaseReason.AUCTION_CANCELED, now);
        pointAccountRepository.save(account);
        pointHoldRepository.save(hold);
        pointTransactionRepository.save(PointTransaction.record(
                account.getUserId(),
                PointTransactionType.RELEASE,
                amount,
                amount,
                -amount,
                account.getAvailableBalance(),
                account.getHeldBalance(),
                PointReferenceType.HOLD,
                String.valueOf(hold.getHoldId()),
                "hold:" + hold.getHoldId() + ":release:auction-canceled",
                null,
                PointHoldReleaseReason.AUCTION_CANCELED.name(),
                "경매 취소 예치 해제",
                now
        ));
        art.setActivePointHold(null);
    }

    private Art findArtForMutation(Long artId) {
        try {
            return artRepository.findByIdForUpdate(artId)
                    .orElseThrow(this::artNotFound);
        } catch (LockTimeoutException
                 | PessimisticLockException
                 | PessimisticLockingFailureException
                 | QueryTimeoutException exception) {
            throw new DomainApiException(
                    HttpStatus.CONFLICT,
                    "ART_UPDATE_CONFLICT",
                    "다른 작품 작업이 처리 중입니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        }
    }

    private void validateMutationAccess(
            Art art,
            String userId,
            LocalDateTime now) {
        Artist artist = art.getArtist();
        User owner = artist == null ? null : artist.getUser();
        boolean isOwnerArtist = userId != null
                && owner != null
                && owner.getUserStatus() != null
                && owner.getUserStatus() == 1
                && userId.equals(owner.getUserId());
        if (!isOwnerArtist) {
            throw new DomainApiException(
                    HttpStatus.FORBIDDEN,
                    "ART_ACCESS_DENIED",
                    "Only the owning artist can modify art"
            );
        }
        if (art.getArtStatus() == null || art.getArtStatus() != Art.STATUS_ACTIVE) {
            throw new DomainApiException(
                    HttpStatus.CONFLICT,
                    "ART_STATUS_CONFLICT",
                    "종료되거나 취소된 작품은 수정하거나 삭제할 수 없습니다."
            );
        }
        if (art.getClosingTime() == null || !now.isBefore(art.getClosingTime())) {
            throw new DomainApiException(
                    HttpStatus.CONFLICT,
                    "AUCTION_CLOSED",
                    "마감된 경매입니다."
            );
        }
    }

    private boolean hasPriceOrPeriodChange(ArtUpdateRequestDto dto) {
        return dto.isStartPriceProvided()
                || dto.isBidStartTimeProvided()
                || dto.isClosingTimeProvided();
    }

    private void validateAuctionPeriod(
            LocalDateTime bidStartTime,
            LocalDateTime closingTime,
            LocalDateTime now) {
        if (bidStartTime == null
                || closingTime == null
                || !closingTime.isAfter(bidStartTime)
                || !closingTime.isAfter(now)) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AUCTION_PERIOD",
                    "Closing time must be after bid start time and current time"
            );
        }
    }

    private void applyUpdate(Art art, ArtUpdateRequestDto dto) {
        if (dto.isStartPriceProvided()) {
            art.setStartPrice(dto.getStartPrice());
            art.setCurrentPrice(dto.getStartPrice());
        }
        if (dto.isBidStartTimeProvided()) {
            art.setBidStartTime(dto.getBidStartTime());
        }
        if (dto.isClosingTimeProvided()) {
            art.setClosingTime(dto.getClosingTime());
        }
        if (dto.isDescriptProvided()) {
            art.setDescript(blankToNull(dto.getDescript()));
        }
        if (dto.isMaterialProvided()) {
            art.setMaterial(blankToNull(dto.getMaterial()));
        }
        if (dto.isFormatProvided()) {
            art.setFormat(dto.getFormat());
        }
        if (dto.isCategoryProvided()) {
            art.setCategory(dto.getCategory());
        }
        if (dto.isWIntroProvided()) {
            art.setWIntro(blankToNull(dto.getWIntro()));
        }
        if (dto.isImgPathProvided()) {
            art.setImgPath(dto.getImgPath().trim());
        }
    }

    private ArtResponseDto toResponse(Art art) {
        Artist artist = art.getArtist();
        return new ArtResponseDto(
                art.getArtId(),
                artist.getArtistCode(),
                artist.getArtistName(),
                art.getName(),
                art.getDescript(),
                art.getMaterial(),
                art.getWIntro(),
                art.getStartPrice(),
                art.getCurrentPrice(),
                art.getBidStartTime(),
                art.getClosingTime(),
                art.getImgPath(),
                art.getArtStatus()
        );
    }

    private MyArtResponseDto toMyArtResponse(MyArtQueryDto art) {
        String result = switch (art.getArtStatus()) {
            case Art.STATUS_SOLD -> "SOLD";
            case Art.STATUS_UNSOLD -> "UNSOLD";
            case Art.STATUS_CANCELED -> "CANCELED";
            default -> "PENDING";
        };
        return new MyArtResponseDto(
                art.getArtId(),
                art.getArtistCode(),
                art.getArtistName(),
                art.getName(),
                art.getDescript(),
                art.getMaterial(),
                art.getWIntro(),
                art.getStartPrice(),
                art.getCurrentPrice(),
                art.getBidStartTime(),
                art.getClosingTime(),
                art.getImgPath(),
                art.getArtStatus(),
                art.getClosedAt(),
                art.getWinningPrice(),
                art.getBidCount(),
                result
        );
    }

    private void validateClassification(ArtFormat format, ArtCategory category) {
        boolean valid = format != null
                && category != null
                && (format == ArtFormat.DIGITAL)
                == (category == ArtCategory.DIGITAL_ART);
        if (!valid) {
            throw new DomainApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ART_CLASSIFICATION",
                    "Digital format requires digital art category and vice versa"
            );
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest createPageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "artId"));
    }

    private DomainApiException artNotFound() {
        return new DomainApiException(HttpStatus.NOT_FOUND, "ART_NOT_FOUND", "Art not found");
    }
}
