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
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
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
import org.springframework.web.server.ResponseStatusException;

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
    private final Clock clock;

    public Page<ArtResponseDto> getActiveArts(int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        return artRepository.findByArtStatus(Art.STATUS_ACTIVE, pageable)
                .map(this::toResponse);
    }

    public ArtDetailResponseDto getArt(Long artId, String userId) {
        Art art = artRepository.findById(artId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Art not found"));
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only artist users can view their arts");
        }

        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Artist profile not found"));
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only artist users can create art");
        }

        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Artist profile not found"));

        if (dto.getClosingTime().isBefore(dto.getBidStartTime()) || dto.getClosingTime().isEqual(dto.getBidStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closing time must be after bid start time");
        }

        Art art = new Art();
        art.setArtist(artist);
        art.setName(dto.getName().trim());
        art.setDescript(blankToNull(dto.getDescript()));
        art.setMaterial(blankToNull(dto.getMaterial()));
        art.setWIntro(blankToNull(dto.getWIntro()));
        art.setStartPrice(dto.getStartPrice());
        art.setCurrentPrice(dto.getStartPrice());
        art.setBidStartTime(dto.getBidStartTime());
        art.setClosingTime(dto.getClosingTime());
        art.setImgPath(dto.getImgPath().trim());
        art.setArtStatus(Art.STATUS_ACTIVE);

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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
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

        applyUpdate(art, dto);
        return toResponse(artRepository.save(art));
    }

    @Transactional
    public ArtDeleteResponseDto deleteArt(Long artId, String userId) {
        Art art = findArtForMutation(artId);
        LocalDateTime now = LocalDateTime.now(clock);
        validateMutationAccess(art, userId, now);

        if (bidRepository.existsByArt(art)) {
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
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

    private Art findArtForMutation(Long artId) {
        try {
            return artRepository.findByIdForUpdate(artId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Art not found"
                    ));
        } catch (LockTimeoutException
                 | PessimisticLockException
                 | PessimisticLockingFailureException
                 | QueryTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
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
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the owning artist can modify art"
            );
        }
        if (art.getArtStatus() == null || art.getArtStatus() != Art.STATUS_ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "종료되거나 취소된 작품은 수정하거나 삭제할 수 없습니다."
            );
        }
        if (art.getClosingTime() == null || !now.isBefore(art.getClosingTime())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest createPageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "artId"));
    }
}
