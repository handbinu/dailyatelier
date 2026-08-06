package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:artist-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ArtistRepositoryTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 5, 12, 0);

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private UserRepository userRepository;

    private Artist alphaFirst;
    private Artist alphaSecond;
    private Artist normalArtist;

    @BeforeEach
    void setUp() {
        alphaFirst = saveArtist("artist-a", "Alpha", 1);
        alphaSecond = saveArtist("artist-b", "Alpha", 1);
        saveArtist("artist-c", "beta Artist", 1);
        normalArtist = saveArtist("normal-user", "Alpha Hidden", 0);
    }

    @Test
    void generatesDistinctArtistCodesWhenMultipleArtistsAreSaved() {
        assertThat(alphaFirst.getArtistCode()).isNotNull();
        assertThat(alphaSecond.getArtistCode()).isNotNull();
        assertThat(alphaFirst.getArtistCode())
                .isNotEqualTo(alphaSecond.getArtistCode());
    }

    @Test
    void searchesArtistUsersByCaseInsensitiveNameAndKeepsStableOrder() {
        Page<ArtistSummaryResponseDto> result = artistRepository.findPublicArtists(
                "ALPHA",
                1,
                Art.STATUS_ACTIVE,
                NOW,
                PageRequest.of(0, 12)
        );

        assertThat(result.getContent())
                .extracting(ArtistSummaryResponseDto::getArtistId)
                .containsExactlyElementsOf(List.of(
                        alphaFirst.getArtistCode(),
                        alphaSecond.getArtistCode()).stream().sorted().toList());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void nullKeywordReturnsAllArtistUsersWithPagination() {
        Page<ArtistSummaryResponseDto> firstPage = artistRepository.findPublicArtists(
                null,
                1,
                Art.STATUS_ACTIVE,
                NOW,
                PageRequest.of(0, 2)
        );

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(ArtistSummaryResponseDto::getArtistId)
                .containsExactlyElementsOf(List.of(
                        alphaFirst.getArtistCode(),
                        alphaSecond.getArtistCode()).stream().sorted().toList());
    }

    @Test
    void countsOnlyArtsThatAreActuallyOpenForBiddingWithoutDuplicatingArtists() {
        saveArt(alphaFirst, "입찰 가능", NOW.minusMinutes(1), NOW.plusMinutes(1), Art.STATUS_ACTIVE);
        saveArt(alphaFirst, "시작 경계", NOW, NOW.plusMinutes(2), Art.STATUS_ACTIVE);
        saveArt(alphaFirst, "시작 전", NOW.plusSeconds(1), NOW.plusMinutes(3), Art.STATUS_ACTIVE);
        saveArt(alphaFirst, "마감 경계", NOW.minusMinutes(2), NOW, Art.STATUS_ACTIVE);
        saveArt(alphaFirst, "이미 유찰", NOW.minusDays(1), NOW.minusHours(1), Art.STATUS_UNSOLD);

        Page<ArtistSummaryResponseDto> result = artistRepository.findPublicArtists(
                "Alpha",
                1,
                Art.STATUS_ACTIVE,
                NOW,
                PageRequest.of(0, 12)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(
                        ArtistSummaryResponseDto::getArtistId,
                        ArtistSummaryResponseDto::getActiveArtCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(alphaFirst.getArtistCode(), 2L),
                        org.assertj.core.groups.Tuple.tuple(alphaSecond.getArtistCode(), 0L));
    }

    @Test
    void detailUsesArtistRoleFilterAndSameActiveArtCountContract() {
        saveArt(alphaSecond, "입찰 가능", NOW.minusHours(1), NOW.plusHours(1), Art.STATUS_ACTIVE);

        ArtistDetailResponseDto detail = artistRepository.findPublicArtistDetail(
                alphaSecond.getArtistCode(),
                1,
                Art.STATUS_ACTIVE,
                NOW
        ).orElseThrow();

        assertThat(detail.getArtistId()).isEqualTo(alphaSecond.getArtistCode());
        assertThat(detail.getActiveArtCount()).isEqualTo(1);
        assertThat(detail.getProfileImagePath()).isEqualTo("/img/artist.png");
        assertThat(artistRepository.findPublicArtistDetail(
                normalArtist.getArtistCode(), 1, Art.STATUS_ACTIVE, NOW)).isEmpty();
    }

    @Test
    void publicArtistArtsExcludeCanceledAndUseClosingTimeThenIdOrder() {
        Art later = saveArt(alphaFirst, "나중 마감", NOW.minusDays(1), NOW.plusHours(2), Art.STATUS_ACTIVE);
        Art sameClosingOlderId = saveArt(alphaFirst, "같은 마감 먼저 저장", NOW.minusDays(2), NOW.plusHours(1), Art.STATUS_UNSOLD);
        Art sameClosingNewerId = saveArt(alphaFirst, "같은 마감 나중 저장", NOW.minusDays(3), NOW.plusHours(1), Art.STATUS_SOLD);
        saveArt(alphaFirst, "취소", NOW.minusDays(1), NOW.plusMinutes(30), Art.STATUS_CANCELED);
        saveArt(alphaSecond, "다른 작가", NOW.minusDays(1), NOW.plusMinutes(10), Art.STATUS_ACTIVE);

        Page<ArtResponseDto> result = artRepository.findPublicArtsByArtistId(
                alphaFirst.getArtistCode(),
                List.of(Art.STATUS_ACTIVE, Art.STATUS_UNSOLD, Art.STATUS_SOLD),
                PageRequest.of(0, 2)
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(ArtResponseDto::getArtId)
                .containsExactly(sameClosingNewerId.getArtId(), sameClosingOlderId.getArtId());

        Page<ArtResponseDto> secondPage = artRepository.findPublicArtsByArtistId(
                alphaFirst.getArtistCode(),
                List.of(Art.STATUS_ACTIVE, Art.STATUS_UNSOLD, Art.STATUS_SOLD),
                PageRequest.of(1, 2)
        );
        assertThat(secondPage.getContent())
                .extracting(ArtResponseDto::getArtId)
                .containsExactly(later.getArtId());
    }

    private Artist saveArtist(
            String userId,
            String artistName,
            int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId.substring(0, Math.min(userId.length(), 10)));
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(NOW.minusDays(30));
        user.setUserStatus(userStatus);
        user = userRepository.save(user);

        Artist artist = new Artist();
        artist.setUser(user);
        artist.setArtistName(artistName);
        artist.setArtistIntro(artistName + " 소개");
        return artistRepository.save(artist);
    }

    private Art saveArt(
            Artist artist,
            String name,
            LocalDateTime bidStartTime,
            LocalDateTime closingTime,
            int status) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(bidStartTime);
        art.setClosingTime(closingTime);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(status);
        return artRepository.save(art);
    }
}
