package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ArtService {
    private static final int ACTIVE_STATUS = 0;
    private static final int MAX_PAGE_SIZE = 50;

    private final ArtRepository artRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;

    public Page<ArtResponseDto> getActiveArts(int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        return artRepository.findByArtStatus(ACTIVE_STATUS, pageable)
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

    public Page<ArtResponseDto> getMyArts(String userId, int page, int size) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only artist users can view their arts");
        }

        Artist artist = artistRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Artist profile not found"));
        return artRepository.findByArtist(artist, createPageRequest(page, size))
                .map(this::toResponse);
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
        art.setArtStatus(0);

        return toResponse(artRepository.save(art));
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest createPageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "artId"));
    }
}
