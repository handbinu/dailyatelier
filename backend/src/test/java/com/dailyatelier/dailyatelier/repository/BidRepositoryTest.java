package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
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
        "spring.datasource.url=jdbc:h2:mem:bid-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BidRepositoryTest {

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private UserRepository userRepository;

    private User bidder;
    private User otherBidder;
    private Art firstArt;
    private Art secondArt;

    @BeforeEach
    void setUp() {
        User sellerUser = createUser("seller", 1);
        Artist artist = new Artist();
        artist.setUser(sellerUser);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        bidder = userRepository.save(createUser("bidder", 0));
        otherBidder = userRepository.save(createUser("other", 0));
        firstArt = saveArt(artist, "첫 번째 작품", 140_000);
        secondArt = saveArt(artist, "두 번째 작품", 120_000);

        saveBid(bidder, firstArt, 110_000, LocalDateTime.of(2026, 7, 20, 10, 0));
        saveBid(bidder, firstArt, 130_000, LocalDateTime.of(2026, 7, 21, 10, 0));
        saveBid(otherBidder, firstArt, 140_000, LocalDateTime.of(2026, 7, 22, 10, 0));
        saveBid(bidder, secondArt, 120_000, LocalDateTime.of(2026, 7, 23, 10, 0));
    }

    @Test
    void queryGroupsByArtSortsByLatestBidAndPaginates() {
        Page<BidSummaryQueryDto> firstPage =
                bidRepository.findBidSummariesByUserId(
                        bidder.getUserId(),
                        PageRequest.of(0, 1)
                );
        Page<BidSummaryQueryDto> secondPage =
                bidRepository.findBidSummariesByUserId(
                        bidder.getUserId(),
                        PageRequest.of(1, 1)
                );

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getArtId()).isEqualTo(secondArt.getArtId());
                    assertThat(summary.getMyBidPrice()).isEqualTo(120_000);
                    assertThat(summary.getLastBidTime())
                            .isEqualTo(LocalDateTime.of(2026, 7, 23, 10, 0));
                });
        assertThat(secondPage.getContent())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getArtId()).isEqualTo(firstArt.getArtId());
                    assertThat(summary.getMyBidPrice()).isEqualTo(130_000);
                    assertThat(summary.getCurrentPrice()).isEqualTo(140_000);
                    assertThat(summary.getLastBidTime())
                            .isEqualTo(LocalDateTime.of(2026, 7, 21, 10, 0));
                });
    }

    @Test
    void queryDoesNotExposeOtherUsersBids() {
        Page<BidSummaryQueryDto> result =
                bidRepository.findBidSummariesByUserId(
                        otherBidder.getUserId(),
                        PageRequest.of(0, 12)
                );

        assertThat(result.getTotalElements()).isOne();
        assertThat(result.getContent())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getArtId()).isEqualTo(firstArt.getArtId());
                    assertThat(summary.getMyBidPrice()).isEqualTo(140_000);
                });
    }

    @Test
    void queryUsesArtIdDescendingWhenLatestBidTimesAreEqual() {
        Art thirdArt = saveArt(firstArt.getArtist(), "세 번째 작품", 125_000);
        saveBid(
                bidder,
                thirdArt,
                125_000,
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        Page<BidSummaryQueryDto> result =
                bidRepository.findBidSummariesByUserId(
                        bidder.getUserId(),
                        PageRequest.of(0, 12)
                );

        assertThat(result.getContent())
                .extracting(BidSummaryQueryDto::getArtId)
                .containsExactly(
                        thirdArt.getArtId(),
                        secondArt.getArtId(),
                        firstArt.getArtId()
                );
    }

    @Test
    void findsHighestBidForAuctionClosing() {
        Bid result = bidRepository
                .findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(firstArt)
                .orElseThrow();

        assertThat(result.getBidPrice()).isEqualTo(140_000);
        assertThat(result.getUser().getUserId()).isEqualTo("other");
    }

    private Art saveArt(Artist artist, String name, int currentPrice) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setStartPrice(100_000);
        art.setCurrentPrice(currentPrice);
        art.setBidStartTime(LocalDateTime.of(2026, 7, 1, 10, 0));
        art.setClosingTime(LocalDateTime.of(2026, 7, 31, 18, 0));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(0);
        return artRepository.save(art);
    }

    private void saveBid(
            User user,
            Art art,
            int bidPrice,
            LocalDateTime bidTime) {
        Bid bid = new Bid();
        bid.setUser(user);
        bid.setArt(art);
        bid.setBidPrice(bidPrice);
        bid.setBidTime(bidTime);
        bidRepository.save(bid);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(userStatus);
        return user;
    }
}
