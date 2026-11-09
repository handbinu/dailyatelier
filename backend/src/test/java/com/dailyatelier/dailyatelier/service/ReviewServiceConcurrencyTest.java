package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ReviewCreateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.ReviewApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:review-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        ReviewService.class,
        ReviewServiceConcurrencyTest.FixedClockConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewServiceConcurrencyTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 26, 12, 0);

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ArtRepository artRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Order order;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(ignored -> {
            reviewRepository.deleteAll();
            orderRepository.deleteAll();
            jdbcTemplate.update("UPDATE art SET winning_bid_id = NULL");
            bidRepository.deleteAll();
            artRepository.deleteAll();
            artistRepository.deleteAll();
            userRepository.deleteAll();

            User buyer = userRepository.save(user("buyer", 0));
            User seller = userRepository.save(user("seller", 1));
            Artist artist = new Artist();
            artist.setUser(seller);
            artist.setArtistName("판매 작가");
            artist = artistRepository.save(artist);

            Art art = new Art();
            art.setArtist(artist);
            art.setName("동시 작성 작품");
            art.setFormat(ArtFormat.PHYSICAL);
            art.setCategory(ArtCategory.OTHER);
            art.setStartPrice(100_000);
            art.setCurrentPrice(150_000);
            art.setMinimumBidIncrement(1_000);
            art.setBidStartTime(NOW.minusDays(2));
            art.setClosingTime(NOW.minusDays(1));
            art.setImgPath("https://example.com/concurrent.jpg");
            art.setArtStatus(Art.STATUS_ACTIVE);
            art = artRepository.save(art);

            Bid bid = new Bid();
            bid.setArt(art);
            bid.setUser(buyer);
            bid.setBidPrice(150_000);
            bid.setBidTime(NOW.minusMinutes(1));
            bid = bidRepository.save(bid);
            art.setWinningBid(bid);
            art.setArtStatus(Art.STATUS_SOLD);
            art.setClosedAt(NOW);
            artRepository.save(art);

            order = Order.create(
                    art,
                    bid,
                    buyer,
                    seller,
                    NOW,
                    NOW.plusHours(24),
                    OrderShippingAddress.of(
                            buyer.getName(), buyer.getPhoneNumber(), "02535",
                            "서울특별시 중랑구", null
                    )
            );
            order.transitionTo(OrderStatus.PAID, NOW.plusMinutes(1), null);
            order.transitionTo(OrderStatus.PREPARING, NOW.plusMinutes(2), null);
            order.transitionTo(OrderStatus.SHIPPED, NOW.plusMinutes(3), null);
            order.transitionTo(OrderStatus.DELIVERED, NOW.plusMinutes(4), null);
            order.transitionTo(OrderStatus.CONFIRMED, NOW.plusMinutes(5), null);
            order = orderRepository.saveAndFlush(order);
        });
    }

    @Test
    void concurrentCreateStoresOneReviewAndReturnsOneConflict() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ReviewCreateRequestDto request = new ReviewCreateRequestDto(
                order.getOrderId(), 9, "동시에 작성하는 충분히 긴 리뷰 내용"
        );

        try {
            List<Future<String>> futures = List.of(
                    executor.submit(() -> createResult(ready, start, request)),
                    executor.submit(() -> createResult(ready, start, request))
            );
            ready.await();
            start.countDown();

            assertThat(List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder("SUCCESS", "REVIEW_ALREADY_EXISTS");
            assertThat(reviewRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String createResult(
            CountDownLatch ready,
            CountDownLatch start,
            ReviewCreateRequestDto request) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            reviewService.create("buyer", request);
            return "SUCCESS";
        } catch (ReviewApiException exception) {
            return exception.getCode();
        }
    }

    private User user(String userId, int status) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(userId);
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(NOW.minusDays(30));
        user.setUserStatus(status);
        return user;
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-08-26T03:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
