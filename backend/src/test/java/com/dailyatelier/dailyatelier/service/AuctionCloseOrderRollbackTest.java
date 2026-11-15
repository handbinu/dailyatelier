package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auction-order-rollback-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AuctionCloseService.class,
        AuctionCloseOrderRollbackTest.FixedClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionCloseOrderRollbackTest {
    private static final LocalDateTime CLOSED_AT =
            LocalDateTime.of(2026, 7, 28, 18, 0);

    @Autowired
    private AuctionCloseService auctionCloseService;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointHoldRepository pointHoldRepository;

    @MockitoBean
    private OrderService orderService;

    private Long artId;

    @BeforeEach
    void setUp() {
        User buyer = userRepository.save(createUser("buyer", 0));
        User seller = createUser("seller", 1);

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.saveAndFlush(artist);

        Art art = new Art();
        art.setFormat(com.dailyatelier.dailyatelier.entity.ArtFormat.PHYSICAL);
        art.setCategory(com.dailyatelier.dailyatelier.entity.ArtCategory.OTHER);
        art.setArtist(artist);
        art.setName("롤백 테스트 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(150_000);
        art.setBidStartTime(CLOSED_AT.minusDays(1));
        art.setClosingTime(CLOSED_AT);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);
        artId = art.getArtId();

        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(buyer);
        bid.setBidPrice(150_000);
        bid.setBidTime(CLOSED_AT.minusMinutes(1));
        bid = bidRepository.save(bid);
        PointHold hold = pointHoldRepository.save(
                PointHold.hold(art, buyer, bid, 150_000, CLOSED_AT.minusMinutes(1)));
        art.setActivePointHold(hold);
        artRepository.saveAndFlush(art);
    }

    @Test
    void orderCreationFailureRollsBackAuctionCloseForRetry() {
        when(orderService.createForSoldAuction(any(), any(), any()))
                .thenThrow(new IllegalStateException("forced order failure"));

        assertThatThrownBy(() -> auctionCloseService.closeAuction(artId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forced order failure");

        Art reloaded = artRepository.findById(artId).orElseThrow();
        assertThat(reloaded.getArtStatus()).isEqualTo(Art.STATUS_ACTIVE);
        assertThat(reloaded.getWinningBid()).isNull();
        assertThat(reloaded.getClosedAt()).isNull();
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(userId);
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(CLOSED_AT.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-07-28T09:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
