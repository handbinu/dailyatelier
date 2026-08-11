package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.dto.ArtSearchSort;
import com.dailyatelier.dailyatelier.dto.ArtSearchStatus;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:art-search-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ArtSearchRepositoryTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 11, 12, 0);

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    private Artist kim;
    private Artist lee;

    @BeforeEach
    void setUp() {
        kim = saveArtist("kim", "김작가");
        lee = saveArtist("lee", "이화가");
    }

    @Test
    void combinesNameArtistFormatCategoryAndOngoingStatus() {
        Art target = saveArt(kim, "푸른 바다", ArtFormat.PHYSICAL,
                ArtCategory.OIL_PAINTING, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW.plusHours(2), 200, NOW.minusDays(1));
        saveArt(lee, "푸른 바다", ArtFormat.PHYSICAL,
                ArtCategory.OIL_PAINTING, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW.plusHours(1), 100, NOW);
        saveArt(kim, "붉은 바다", ArtFormat.PHYSICAL,
                ArtCategory.OIL_PAINTING, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW.plusHours(1), 100, NOW);

        Page<Art> result = artRepository.search(new ArtSearchCriteria(
                "푸른", "김", ArtFormat.PHYSICAL,
                ArtCategory.OIL_PAINTING, ArtSearchStatus.ONGOING,
                ArtSearchSort.ENDING_SOON
        ), NOW, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Art::getArtId)
                .containsExactly(target.getArtId());
    }

    @Test
    void appliesTimeBoundariesAndExcludesCanceledArt() {
        Art upcoming = saveArt(kim, "예정", ArtFormat.DIGITAL,
                ArtCategory.DIGITAL_ART, Art.STATUS_ACTIVE,
                NOW.plusSeconds(1), NOW.plusHours(2), 100, NOW);
        Art ongoing = saveArt(kim, "진행", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_ACTIVE,
                NOW, NOW.plusSeconds(1), 100, NOW);
        Art expiredActive = saveArt(kim, "스케줄 전 종료", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW, 100, NOW);
        Art sold = saveArt(kim, "판매", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_SOLD,
                NOW.minusDays(2), NOW.minusDays(1), 100, NOW);
        saveArt(kim, "취소", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_CANCELED,
                NOW.minusHours(1), NOW.plusHours(1), 100, NOW);

        assertStatus(ArtSearchStatus.UPCOMING, upcoming);
        assertStatus(ArtSearchStatus.ONGOING, ongoing);
        assertThat(search(ArtSearchStatus.ENDED).getContent())
                .containsExactlyInAnyOrder(expiredActive, sold);
        assertThat(search(null).getContent()).hasSize(4);
    }

    @Test
    void appliesAllSortsAndIdDescendingTieBreaker() {
        Art first = saveArt(kim, "첫째", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW.plusHours(2), 200, NOW.minusDays(1));
        Art second = saveArt(kim, "둘째", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_ACTIVE,
                NOW.minusHours(1), NOW.plusHours(2), 200, NOW);
        Art ended = saveArt(kim, "종료", ArtFormat.PHYSICAL,
                ArtCategory.OTHER, Art.STATUS_SOLD,
                NOW.minusDays(2), NOW.minusDays(1), 50, NOW.plusDays(1));

        assertIds(ArtSearchSort.ENDING_SOON, second, first, ended);
        assertIds(ArtSearchSort.NEWEST, ended, second, first);
        assertIds(ArtSearchSort.PRICE_ASC, ended, second, first);
        assertIds(ArtSearchSort.PRICE_DESC, second, first, ended);
    }

    private void assertStatus(ArtSearchStatus status, Art expected) {
        assertThat(search(status).getContent()).containsExactly(expected);
    }

    private Page<Art> search(ArtSearchStatus status) {
        return artRepository.search(new ArtSearchCriteria(
                null, null, null, null, status, ArtSearchSort.NEWEST
        ), NOW, PageRequest.of(0, 20));
    }

    private void assertIds(ArtSearchSort sort, Art... expected) {
        Page<Art> result = artRepository.search(new ArtSearchCriteria(
                null, null, null, null, null, sort
        ), NOW, PageRequest.of(0, 20));
        assertThat(result.getContent()).extracting(Art::getArtId)
                .containsExactly(java.util.Arrays.stream(expected)
                        .map(Art::getArtId).toArray(Long[]::new));
    }

    private Artist saveArtist(String userId, String artistName) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("password");
        user.setName("사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(NOW.minusDays(10));
        user.setUserStatus(1);
        Artist artist = new Artist();
        artist.setUser(user);
        artist.setArtistName(artistName);
        return artistRepository.save(artist);
    }

    private Art saveArt(
            Artist artist, String name, ArtFormat format, ArtCategory category,
            int status, LocalDateTime start, LocalDateTime close,
            int price, LocalDateTime createdAt) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setFormat(format);
        art.setCategory(category);
        art.setStartPrice(price);
        art.setCurrentPrice(price);
        art.setBidStartTime(start);
        art.setClosingTime(close);
        art.setImgPath("image.jpg");
        art.setArtStatus(status);
        art.setCreatedAt(createdAt);
        return artRepository.saveAndFlush(art);
    }
}
