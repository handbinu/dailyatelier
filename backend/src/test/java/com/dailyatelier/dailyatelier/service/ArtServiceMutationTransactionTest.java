package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.LikeItemDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Likes;
import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.entity.PointHoldReleaseReason;
import com.dailyatelier.dailyatelier.entity.PointHoldStatus;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import com.dailyatelier.dailyatelier.entity.Review;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:art-mutation-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        ArtService.class,
        ArtServiceMutationTransactionTest.FixedClockConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ArtServiceMutationTransactionTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 15, 0);

    @Autowired
    private ArtService artService;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private PointHoldRepository pointHoldRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Artist artist;
    private User liker;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE art SET active_point_hold_id = NULL");
        pointTransactionRepository.deleteAll();
        pointHoldRepository.deleteAll();
        pointAccountRepository.deleteAll();
        reviewRepository.deleteAll();
        likesRepository.deleteAll();
        bidRepository.deleteAll();
        artRepository.deleteAll();
        artistRepository.deleteAll();
        userRepository.deleteAll();

        User owner = createUser("owner", 1);
        artist = new Artist();
        artist.setUser(owner);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);
        liker = userRepository.save(createUser("liker", 0));
    }

    @Test
    void physicalDeleteKeepsLikeAsDeletedArtItem() {
        Art art = saveActiveArt("삭제 대상");
        Long artId = art.getArtId();
        saveLike(art, liker);

        Page<LikeItemDto> liveItems = likesRepository.findLikeItemsByUserId(
                liker.getUserId(),
                PageRequest.of(0, 12)
        );
        assertThat(liveItems.getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getArtDeleted()).isFalse();
                    assertThat(item.getAvailabilityMessage()).isNull();
                });

        ArtDeleteResponseDto response =
                artService.deleteArt(artId, "owner");

        assertThat(response.getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.DELETED);
        assertThat(artRepository.findById(artId)).isEmpty();
        assertThat(likesRepository.findAll())
                .singleElement()
                .satisfies(like -> assertThat(like.getArt()).isNull());

        Page<LikeItemDto> items = likesRepository.findLikeItemsByUserId(
                liker.getUserId(),
                PageRequest.of(0, 12)
        );
        assertThat(items.getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getArtId()).isNull();
                    assertThat(item.getArtDeleted()).isTrue();
                    assertThat(item.getAvailabilityMessage())
                            .isEqualTo("없어진 작품입니다.");
                });
    }

    @Test
    void reviewPreventsPhysicalDeleteAndKeepsLikeAttached() {
        Art art = saveActiveArt("리뷰 보유 작품");
        Long artId = art.getArtId();
        saveLike(art, liker);

        Review review = new Review();
        review.setArt(art);
        review.setUser(liker);
        review.setContent("삭제를 막는 리뷰");
        reviewRepository.save(review);

        assertThatThrownBy(() -> artService.deleteArt(artId, "owner"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(
                        ((ResponseStatusException) error).getStatusCode()
                ).isEqualTo(HttpStatus.CONFLICT));

        assertThat(artRepository.findById(artId)).isPresent();
        assertThat(likesRepository.findAll())
                .singleElement()
                .satisfies(like -> assertThat(like.getArt().getArtId())
                        .isEqualTo(artId));
    }

    @Test
    void bidChangesDeleteIntoCancelAndPreservesHistory() {
        Art art = saveActiveArt("입찰 보유 작품");
        Long artId = art.getArtId();
        saveLike(art, liker);

        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(liker);
        bid.setBidPrice(120_000);
        bid.setBidTime(NOW.minusHours(1));
        bidRepository.save(bid);

        ArtDeleteResponseDto response =
                artService.deleteArt(artId, "owner");

        Art canceled = artRepository.findById(artId).orElseThrow();
        assertThat(response.getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.CANCELED);
        assertThat(canceled.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
        assertThat(canceled.getClosedAt()).isEqualTo(NOW);
        assertThat(bidRepository.count()).isEqualTo(1);
        assertThat(likesRepository.findAll())
                .singleElement()
                .satisfies(like -> assertThat(like.getArt().getArtId())
                        .isEqualTo(artId));
    }

    @Test
    void cancelReleasesActiveHoldAndRestoresAvailableBalanceAtomically() {
        Art art = saveActiveArt("예치 보유 작품");
        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(liker);
        bid.setBidPrice(120_000);
        bid.setBidTime(NOW.minusHours(1));
        bid = bidRepository.save(bid);
        jdbcTemplate.update("""
                INSERT INTO point_account (
                    user_id, available_balance, held_balance, version, created_at, updated_at
                ) VALUES (?, 80000, 120000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, liker.getUserId());
        PointHold hold = pointHoldRepository.save(
                PointHold.hold(art, liker, bid, 120_000L, NOW.minusHours(1))
        );
        art.setActivePointHold(hold);
        art.setCurrentPrice(120_000);
        artRepository.save(art);

        artService.deleteArt(art.getArtId(), "owner");

        assertThat(pointAccountRepository.findById(liker.getUserId()).orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isEqualTo(200_000L);
                    assertThat(account.getHeldBalance()).isZero();
                });
        assertThat(pointHoldRepository.findById(hold.getHoldId()).orElseThrow())
                .satisfies(released -> {
                    assertThat(released.getStatus()).isEqualTo(PointHoldStatus.RELEASED);
                    assertThat(released.getReleaseReason())
                            .isEqualTo(PointHoldReleaseReason.AUCTION_CANCELED);
                });
        assertThat(pointTransactionRepository.findAll())
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getType()).isEqualTo(PointTransactionType.RELEASE);
                    assertThat(transaction.getAvailableDelta()).isEqualTo(120_000L);
                    assertThat(transaction.getHeldDelta()).isEqualTo(-120_000L);
                });
        assertThat(artRepository.findById(art.getArtId()).orElseThrow())
                .satisfies(canceled -> {
                    assertThat(canceled.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
                    assertThat(canceled.getActivePointHold()).isNull();
                });
    }

    private Art saveActiveArt(String name) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setDescript("설명");
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(NOW.minusDays(1));
        art.setClosingTime(NOW.plusDays(1));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        return artRepository.save(art);
    }

    private void saveLike(Art art, User user) {
        Likes like = new Likes();
        like.setArt(art);
        like.setUser(user);
        likesRepository.save(like);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(NOW.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-07-28T06:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
