package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:expired-art-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ArtRepositoryExpiredAuctionTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 27, 18, 0);

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    private Artist artist;

    @BeforeEach
    void setUp() {
        User seller = new User();
        seller.setUserId("seller");
        seller.setPassword("encoded-password");
        seller.setName("테스트 사용자");
        seller.setNickname("seller");
        seller.setPhoneNumber("010-0000-0000");
        seller.setEmail("seller@example.com");
        seller.setJoinDate(LocalDateTime.of(2026, 7, 1, 10, 0));
        seller.setUserStatus(1);

        artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);
    }

    @Test
    void findsOnlyExpiredActiveArtsInClosingOrderWithBatchLimit() {
        Art oldest = saveArt("가장 먼저 마감", NOW.minusMinutes(2), Art.STATUS_ACTIVE);
        Art sameBoundary = saveArt("경계 시각 마감", NOW, Art.STATUS_ACTIVE);
        saveArt("아직 진행 중", NOW.plusSeconds(1), Art.STATUS_ACTIVE);
        saveArt("이미 유찰", NOW.minusMinutes(3), Art.STATUS_UNSOLD);
        saveArt("이미 낙찰", NOW.minusMinutes(4), Art.STATUS_SOLD);

        List<Long> firstBatch = artRepository.findExpiredActiveArtIds(
                Art.STATUS_ACTIVE,
                NOW,
                PageRequest.of(0, 1)
        );
        List<Long> allTargets = artRepository.findExpiredActiveArtIds(
                Art.STATUS_ACTIVE,
                NOW,
                PageRequest.of(0, 10)
        );

        assertThat(firstBatch).containsExactly(oldest.getArtId());
        assertThat(allTargets).containsExactly(
                oldest.getArtId(),
                sameBoundary.getArtId()
        );
    }

    private Art saveArt(String name, LocalDateTime closingTime, int artStatus) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(NOW.minusDays(1));
        art.setClosingTime(closingTime);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(artStatus);
        return artRepository.save(art);
    }
}
