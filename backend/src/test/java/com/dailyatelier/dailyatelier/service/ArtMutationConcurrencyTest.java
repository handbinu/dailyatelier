package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtUpdateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        "spring.datasource.url=jdbc:h2:mem:art-mutation-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        ArtService.class,
        BidService.class,
        AuctionCloseService.class,
        OrderService.class,
        ShippingAddressPolicy.class,
        ArtMutationConcurrencyTest.MutableClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ArtMutationConcurrencyTest {
    private static final Instant OPEN_INSTANT =
            Instant.parse("2026-07-28T06:00:00Z");
    private static final LocalDateTime OPEN_TIME =
            LocalDateTime.of(2026, 7, 28, 15, 0);

    @Autowired
    private ArtService artService;

    @Autowired
    private BidService bidService;

    @Autowired
    private AuctionCloseService auctionCloseService;

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
    private OrderRepository orderRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MutableClock clock;

    private ExecutorService executor;
    private Artist artist;
    private Long artId;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        likesRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        bidRepository.deleteAll();
        artRepository.deleteAll();
        artistRepository.deleteAll();
        userRepository.deleteAll();

        clock.setInstant(OPEN_INSTANT);

        User owner = createUser("owner", 1);
        artist = new Artist();
        artist.setUser(owner);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);
        userRepository.save(createUser("bidder", 0));

        artId = saveOpenArt("경합 대상 작품").getArtId();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void updateCommittedBeforeBidMakesBidUseLatestPrice() throws Exception {
        CountDownLatch updateReady = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        ArtUpdateRequestDto updateRequest = new ArtUpdateRequestDto();
        updateRequest.setStartPrice(110_000);

        Future<ArtResponseDto> updateFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    ArtResponseDto response =
                            artService.updateArt(artId, "owner", updateRequest);
                    updateReady.countDown();
                    await(releaseUpdate);
                    return response;
                }));
        assertThat(updateReady.await(5, TimeUnit.SECONDS)).isTrue();

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                bidService.createBid(
                        artId,
                        "bidder",
                        bidRequest(120_000)
                ));
        assertBlocked(bidFuture, releaseUpdate);

        updateFuture.get(5, TimeUnit.SECONDS);
        BidCreateResponseDto bid = bidFuture.get(5, TimeUnit.SECONDS);
        Art persisted = artRepository.findById(artId).orElseThrow();
        assertThat(bid.getCurrentPrice()).isEqualTo(120_000);
        assertThat(persisted.getStartPrice()).isEqualTo(110_000);
        assertThat(persisted.getCurrentPrice()).isEqualTo(120_000);
    }

    @Test
    void bidCommittedBeforeUpdateMakesPriceChangeFail() throws Exception {
        CountDownLatch bidReady = new CountDownLatch(1);
        CountDownLatch releaseBid = new CountDownLatch(1);

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    BidCreateResponseDto response = bidService.createBid(
                            artId,
                            "bidder",
                            bidRequest(120_000)
                    );
                    bidReady.countDown();
                    await(releaseBid);
                    return response;
                }));
        assertThat(bidReady.await(5, TimeUnit.SECONDS)).isTrue();

        ArtUpdateRequestDto updateRequest = new ArtUpdateRequestDto();
        updateRequest.setStartPrice(110_000);
        Future<ArtResponseDto> updateFuture = executor.submit(() ->
                artService.updateArt(artId, "owner", updateRequest));
        assertBlocked(updateFuture, releaseBid);

        bidFuture.get(5, TimeUnit.SECONDS);
        assertResponseStatus(updateFuture, HttpStatus.CONFLICT);
        Art persisted = artRepository.findById(artId).orElseThrow();
        assertThat(persisted.getStartPrice()).isEqualTo(100_000);
        assertThat(persisted.getCurrentPrice()).isEqualTo(120_000);
        assertThat(bidRepository.count()).isEqualTo(1);
    }

    @Test
    void bidCommittedBeforeDeleteChangesDeleteIntoCancel() throws Exception {
        CountDownLatch bidReady = new CountDownLatch(1);
        CountDownLatch releaseBid = new CountDownLatch(1);

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    BidCreateResponseDto response = bidService.createBid(
                            artId,
                            "bidder",
                            bidRequest(120_000)
                    );
                    bidReady.countDown();
                    await(releaseBid);
                    return response;
                }));
        assertThat(bidReady.await(5, TimeUnit.SECONDS)).isTrue();

        Future<ArtDeleteResponseDto> deleteFuture = executor.submit(() ->
                artService.deleteArt(artId, "owner"));
        assertBlocked(deleteFuture, releaseBid);

        bidFuture.get(5, TimeUnit.SECONDS);
        ArtDeleteResponseDto deletion =
                deleteFuture.get(5, TimeUnit.SECONDS);
        assertThat(deletion.getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.CANCELED);
        assertThat(artRepository.findById(artId).orElseThrow().getArtStatus())
                .isEqualTo(Art.STATUS_CANCELED);
        assertThat(bidRepository.count()).isEqualTo(1);
    }

    @Test
    void physicalDeleteCommittedBeforeBidMakesBidFailNotFound() throws Exception {
        CountDownLatch deleteReady = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);

        Future<ArtDeleteResponseDto> deleteFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    ArtDeleteResponseDto response =
                            artService.deleteArt(artId, "owner");
                    deleteReady.countDown();
                    await(releaseDelete);
                    return response;
                }));
        assertThat(deleteReady.await(5, TimeUnit.SECONDS)).isTrue();

        Future<BidCreateResponseDto> bidFuture = executor.submit(() ->
                bidService.createBid(
                        artId,
                        "bidder",
                        bidRequest(120_000)
                ));
        assertBlocked(bidFuture, releaseDelete);

        assertThat(deleteFuture.get(5, TimeUnit.SECONDS).getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.DELETED);
        assertBidError(bidFuture, "ART_NOT_FOUND");
        assertThat(artRepository.findById(artId)).isEmpty();
        assertThat(bidRepository.count()).isZero();
    }

    @Test
    void updateCommittedBeforeCloseMakesCloseUseLatestData() throws Exception {
        CountDownLatch updateReady = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        ArtUpdateRequestDto updateRequest = new ArtUpdateRequestDto();
        updateRequest.setDescript("마감에 반영할 최신 설명");
        updateRequest.setClosingTime(OPEN_TIME.plusHours(2));

        Future<ArtResponseDto> updateFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    ArtResponseDto response =
                            artService.updateArt(artId, "owner", updateRequest);
                    updateReady.countDown();
                    await(releaseUpdate);
                    return response;
                }));
        assertThat(updateReady.await(5, TimeUnit.SECONDS)).isTrue();

        clock.setInstant(Instant.parse("2026-07-28T08:00:00Z"));
        Future<AuctionCloseResult> closeFuture = executor.submit(() ->
                auctionCloseService.closeAuction(artId));
        assertBlocked(closeFuture, releaseUpdate);

        updateFuture.get(5, TimeUnit.SECONDS);
        assertThat(closeFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(AuctionCloseResult.UNSOLD);
        Art closed = artRepository.findById(artId).orElseThrow();
        assertThat(closed.getDescript())
                .isEqualTo("마감에 반영할 최신 설명");
        assertThat(closed.getClosingTime())
                .isEqualTo(OPEN_TIME.plusHours(2));
        assertThat(closed.getArtStatus()).isEqualTo(Art.STATUS_UNSOLD);
    }

    @Test
    void closeCommittedBeforeDeleteMakesDeleteFail() throws Exception {
        clock.setInstant(Instant.parse("2026-07-28T07:00:00Z"));
        CountDownLatch closeReady = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);

        Future<AuctionCloseResult> closeFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    AuctionCloseResult result =
                            auctionCloseService.closeAuction(artId);
                    closeReady.countDown();
                    await(releaseClose);
                    return result;
                }));
        assertThat(closeReady.await(5, TimeUnit.SECONDS)).isTrue();

        Future<ArtDeleteResponseDto> deleteFuture = executor.submit(() ->
                artService.deleteArt(artId, "owner"));
        assertBlocked(deleteFuture, releaseClose);

        assertThat(closeFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(AuctionCloseResult.UNSOLD);
        assertResponseStatus(deleteFuture, HttpStatus.CONFLICT);
        assertThat(artRepository.findById(artId).orElseThrow().getArtStatus())
                .isEqualTo(Art.STATUS_UNSOLD);
    }

    @Test
    void physicalDeleteCommittedBeforeUpdateMakesUpdateFailNotFound() throws Exception {
        CountDownLatch deleteReady = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);

        Future<ArtDeleteResponseDto> deleteFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    ArtDeleteResponseDto response =
                            artService.deleteArt(artId, "owner");
                    deleteReady.countDown();
                    await(releaseDelete);
                    return response;
                }));
        assertThat(deleteReady.await(5, TimeUnit.SECONDS)).isTrue();

        ArtUpdateRequestDto updateRequest = new ArtUpdateRequestDto();
        updateRequest.setDescript("삭제 뒤 반영되면 안 되는 설명");
        Future<ArtResponseDto> updateFuture = executor.submit(() ->
                artService.updateArt(artId, "owner", updateRequest));
        assertBlocked(updateFuture, releaseDelete);

        assertThat(deleteFuture.get(5, TimeUnit.SECONDS).getAction())
                .isEqualTo(ArtDeleteResponseDto.Action.DELETED);
        assertResponseStatus(updateFuture, HttpStatus.NOT_FOUND);
        assertThat(artRepository.findById(artId)).isEmpty();
    }

    @Test
    void lockOnOneArtDoesNotBlockDeleteOnAnotherArt() throws Exception {
        Long otherArtId = saveOpenArt("독립 삭제 작품").getArtId();
        CountDownLatch updateReady = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        ArtUpdateRequestDto updateRequest = new ArtUpdateRequestDto();
        updateRequest.setDescript("잠금 유지");

        Future<ArtResponseDto> updateFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    ArtResponseDto response =
                            artService.updateArt(artId, "owner", updateRequest);
                    updateReady.countDown();
                    await(releaseUpdate);
                    return response;
                }));
        assertThat(updateReady.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            ArtDeleteResponseDto deletion = executor.submit(() ->
                            artService.deleteArt(otherArtId, "owner"))
                    .get(2, TimeUnit.SECONDS);
            assertThat(deletion.getAction())
                    .isEqualTo(ArtDeleteResponseDto.Action.DELETED);
        } finally {
            releaseUpdate.countDown();
        }

        updateFuture.get(5, TimeUnit.SECONDS);
        assertThat(artRepository.findById(artId)).isPresent();
        assertThat(artRepository.findById(otherArtId)).isEmpty();
    }

    private void assertBlocked(Future<?> future, CountDownLatch releaseLatch) {
        try {
            assertThatThrownBy(() -> future.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseLatch.countDown();
        }
    }

    private void assertResponseStatus(
            Future<?> future,
            HttpStatus expectedStatus) {
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException cause =
                            (ResponseStatusException) error.getCause();
                    assertThat(cause.getStatusCode()).isEqualTo(expectedStatus);
                });
    }

    private void assertBidError(Future<?> future, String expectedCode) {
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BidApiException.class)
                .satisfies(error -> {
                    BidApiException cause = (BidApiException) error.getCause();
                    assertThat(cause.getCode()).isEqualTo(expectedCode);
                });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private Art saveOpenArt(String name) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setDescript("기존 설명");
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(OPEN_TIME.minusHours(1));
        art.setClosingTime(OPEN_TIME.plusHours(1));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        return artRepository.save(art);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(OPEN_TIME.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }

    private BidCreateRequestDto bidRequest(int bidPrice) {
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(bidPrice);
        return request;
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
                    OPEN_INSTANT,
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
