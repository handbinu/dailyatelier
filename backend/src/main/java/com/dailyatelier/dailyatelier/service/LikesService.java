package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.LikeItemDto;
import com.dailyatelier.dailyatelier.dto.LikeStatusDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Likes;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikesService {
    private final LikesRepository likesRepository;
    private final UserRepository userRepository;
    private final ArtRepository artRepository;

    public Page<LikeItemDto> getMyLikes(String userId, Pageable pageable) {
        ensureUser(userId);
        return likesRepository.findLikeItemsByUserId(userId, pageable);
    }

    @Transactional
    public LikeStatusDto addLike(String userId, Long artId) {
        Art art = getArtForUpdate(artId);
        User user = getUser(userId);

        if (likesRepository.findByUserAndArtForUpdate(user, art).isEmpty()) {
            Likes like = new Likes();
            like.setUser(user);
            like.setArt(art);
            likesRepository.save(like);
        }

        return new LikeStatusDto(artId, true);
    }

    @Transactional
    public LikeStatusDto removeLike(String userId, Long artId) {
        User user = getUser(userId);
        Art art = getArt(artId);
        likesRepository.deleteByUserAndArt(user, art);
        return new LikeStatusDto(artId, false);
    }

    public LikeStatusDto getLikeStatus(String userId, Long artId) {
        User user = getUser(userId);
        Art art = getArt(artId);
        return new LikeStatusDto(artId, likesRepository.existsByUserAndArt(user, art));
    }

    private void ensureUser(String userId) {
        if (!userRepository.existsByUserId(userId)) {
            throw userNotFound();
        }
    }

    private User getUser(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw userNotFound();
        }
        return user;
    }

    private Art getArt(Long artId) {
        return artRepository.findById(artId)
                .orElseThrow(this::artNotFound);
    }

    private Art getArtForUpdate(Long artId) {
        return artRepository.findByIdForUpdate(artId)
                .orElseThrow(this::artNotFound);
    }

    private DomainApiException userNotFound() {
        return new DomainApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다."
        );
    }

    private DomainApiException artNotFound() {
        return new DomainApiException(
                HttpStatus.NOT_FOUND,
                "ART_NOT_FOUND",
                "작품을 찾을 수 없습니다."
        );
    }
}
