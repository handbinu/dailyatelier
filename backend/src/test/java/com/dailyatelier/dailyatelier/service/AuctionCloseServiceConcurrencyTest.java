package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auction-close-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        AuctionCloseService.class,
        BidService.class,
        AuctionCloseServiceConcurrencyTest.MutableClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionCloseServiceConcurrencyTest {

    @Autowired
    private AuctionCloseService auctionCloseService;

    @Autowired
    private BidService bidService;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private Long artId;

    @BeforeEach
    void setUp() {
        resetDatabase();
        clock.setInstant(Instant.parse("2026-07-27T09:00:00Z"));
        User seller = createUser("sellerC");
        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);
        userRepository.save(createUser("bidderC"));

        Art art = new Art();
        art.setArtist(artist);
        art.setName("동시 마감 테스트 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(LocalDateTime.of(2026, 7, 26, 18, 0));
        art.setClosingTime(LocalDateTime.of(2026, 7, 27, 18, 0));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        artId = artRepository.save(art).getArtId();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void concurrentCloseCallsPersistOneResult() throws Exception {
        Future<AuctionCloseResult> first =
                executor.submit(() -> auctionCloseService.closeAuction(artId));
        Future<AuctionCloseResult> second =
                executor.submit(() -> auctionCloseService.closeAuction(artId));

        List<AuctionCloseResult> results =
                List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

        assertThat(results)
                .containsExactlyInAnyOrder(
                        AuctionCloseResult.UNSOLD,
                        AuctionCloseResult.ALREADY_CLOSED
                );
        Art closedArt = artRepository.findById(artId).orElseThrow();
        assertThat(closedArt.getArtStatus()).isEqualTo(Art.STATUS_UNSOLD);
        assertThat(closedArt.getWinningBid()).isNull();
        assertThat(closedArt.getClosedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
    }

    @Test
    void bidCommittedBeforeClosingLockIsIncludedInWinningResult() throws Exception {
        clock.setInstant(Instant.parse("2026-07-27T08:59:59Z"));
        CountDownLatch bidSaved = new CountDownLatch(1);
        CountDownLatch releaseBid = new CountDownLatch(1);

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    BidCreateResponseDto response = bidService.createBid(
                            artId,
                            "bidderC",
                            createBidRequest(120_000)
                    );
                    bidSaved.countDown();
                    await(releaseBid);
                    return response;
                }));
        assertThat(bidSaved.await(5, TimeUnit.SECONDS)).isTrue();

        clock.setInstant(Instant.parse("2026-07-27T09:00:00Z"));
        Future<AuctionCloseResult> closeFuture =
                executor.submit(() -> auctionCloseService.closeAuction(artId));

        try {
            assertThatThrownBy(() -> closeFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseBid.countDown();
        }

        bidFuture.get(5, TimeUnit.SECONDS);
        assertThat(closeFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(AuctionCloseResult.SOLD);

        Art closedArt = artRepository.findById(artId).orElseThrow();
        assertThat(closedArt.getArtStatus()).isEqualTo(Art.STATUS_SOLD);
        assertThat(closedArt.getWinningBid()).isNotNull();
        assertThat(bidRepository.findById(closedArt.getWinningBid().getBidId()).orElseThrow()
                .getBidPrice()).isEqualTo(120_000);
    }

    @Test
    void bidWaitingForClosingLockIsRejectedAfterCloseCommits() throws Exception {
        CountDownLatch closeFinished = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);

        Future<AuctionCloseResult> closeFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    AuctionCloseResult result = auctionCloseService.closeAuction(artId);
                    closeFinished.countDown();
                    await(releaseClose);
                    return result;
                }));
        assertThat(closeFinished.await(5, TimeUnit.SECONDS)).isTrue();

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                bidService.createBid(artId, "bidderC", createBidRequest(120_000)));

        try {
            assertThatThrownBy(() -> bidFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseClose.countDown();
        }

        assertThat(closeFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(AuctionCloseResult.UNSOLD);
        assertThatThrownBy(() -> bidFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BidApiException.class)
                .satisfies(error -> {
                    BidApiException cause = (BidApiException) error.getCause();
                    assertThat(cause.getCode()).isEqualTo("AUCTION_CLOSED");
                });
        assertThat(bidRepository.count()).isZero();
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 7, 1, 10, 0));
        user.setUserStatus(1);
        return user;
    }

    private BidCreateRequestDto createBidRequest(int bidPrice) {
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(bidPrice);
        return request;
    }

    private void resetDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE bid");
        jdbcTemplate.execute("TRUNCATE TABLE art");
        jdbcTemplate.execute("TRUNCATE TABLE artist");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        MutableClock clock() {
            return new MutableClock(
                    Instant.parse("2026-07-27T09:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = new AtomicReference<>(instant);
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
