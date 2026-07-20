package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ArtService {
    private final ArtRepository artRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;

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
}
