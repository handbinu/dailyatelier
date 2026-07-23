package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtServiceTest {

    @Mock
    private ArtRepository artRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ArtService artService;

    @Test
    void getActiveArtsUsesActiveStatusLatestOrderAndSizeLimit() {
        Art art = createArt(1L, 0, "artist-user");
        when(artRepository.findByArtStatus(eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(art)));

        Page<ArtResponseDto> result = artService.getActiveArts(2, 100);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getArtId()).isEqualTo(1L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(artRepository).findByArtStatus(eq(0), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("artId").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getActiveArtsReturnsEmptyPageWhenRequestedPageIsOutOfRange() {
        when(artRepository.findByArtStatus(eq(0), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        Page<ArtResponseDto> result = artService.getActiveArts(99, 12);

        assertThat(result.getNumber()).isEqualTo(99);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getArtReturnsEveryStatusAndMarksOwner() {
        Art art = createArt(7L, 2, "owner-id");
        when(artRepository.findById(7L)).thenReturn(Optional.of(art));

        ArtDetailResponseDto result = artService.getArt(7L, "owner-id");

        assertThat(result.getArtStatus()).isEqualTo(2);
        assertThat(result.getIsOwner()).isTrue();
    }

    @Test
    void getArtMarksAnonymousAndOtherUsersAsNotOwner() {
        Art art = createArt(8L, 1, "owner-id");
        when(artRepository.findById(8L)).thenReturn(Optional.of(art));

        assertThat(artService.getArt(8L, null).getIsOwner()).isFalse();
        assertThat(artService.getArt(8L, "other-id").getIsOwner()).isFalse();
    }

    @Test
    void getArtThrowsNotFoundForMissingArt() {
        when(artRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artService.getArt(404L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getMyArtsReturnsAllStatusesForLoggedInArtist() {
        User user = createUser("artist-user", 1);
        Artist artist = createArtist(user);
        Art endedArt = createArt(11L, 1, user.getUserId());
        Art wonArt = createArt(12L, 2, user.getUserId());
        when(userRepository.findByUserId(user.getUserId())).thenReturn(user);
        when(artistRepository.findByUser(user)).thenReturn(Optional.of(artist));
        when(artRepository.findByArtist(eq(artist), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(endedArt, wonArt)));

        Page<ArtResponseDto> result = artService.getMyArts(user.getUserId(), 1, 12);

        assertThat(result.getContent())
                .extracting(ArtResponseDto::getArtStatus)
                .containsExactly(1, 2);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(artRepository).findByArtist(eq(artist), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("artId").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getMyArtsRejectsNonArtistUser() {
        User user = createUser("normal-user", 0);
        when(userRepository.findByUserId(user.getUserId())).thenReturn(user);

        assertThatThrownBy(() -> artService.getMyArts(user.getUserId(), 0, 12))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Art createArt(Long artId, int artStatus, String ownerUserId) {
        Art art = new Art();
        art.setArtId(artId);
        art.setArtist(createArtist(createUser(ownerUserId, 1)));
        art.setName("테스트 작품 " + artId);
        art.setDescript("작품 설명");
        art.setMaterial("캔버스");
        art.setWIntro("작가 소개");
        art.setStartPrice(100_000);
        art.setCurrentPrice(120_000);
        art.setBidStartTime(LocalDateTime.of(2026, 7, 1, 10, 0));
        art.setClosingTime(LocalDateTime.of(2026, 7, 31, 18, 0));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(artStatus);
        return art;
    }

    private Artist createArtist(User user) {
        Artist artist = new Artist();
        artist.setArtistCode("artist-code-" + user.getUserId());
        artist.setUser(user);
        artist.setArtistName("테스트 작가");
        return artist;
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setUserStatus(userStatus);
        return user;
    }
}
