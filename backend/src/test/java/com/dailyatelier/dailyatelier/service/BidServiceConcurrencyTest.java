package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bid-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({BidService.class, PointAccountService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BidServiceConcurrencyTest {

    @Autowired
    private BidService bidService;

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private BidRepository bidRepository;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private ExecutorService executor;
    private Artist seller;
    private Long artId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE art SET active_point_hold_id = NULL");
        pointTransactionRepository.deleteAll();
        pointHoldRepository.deleteAll();
        pointAccountRepository.deleteAll();
        bidRepository.deleteAll();
        artRepository.deleteAll();
        artistRepository.deleteAll();
        userRepository.deleteAll();

        User sellerUser = createUser("seller", 1);
        seller = new Artist();
        seller.setUser(sellerUser);
        seller.setArtistName("테스트 작가");
        seller = artistRepository.save(seller);
        userRepository.save(createUser("bidder", 0));
        jdbcTemplate.update("""
                INSERT INTO point_account (
                    user_id, available_balance, held_balance, version, created_at, updated_at
                ) VALUES ('bidder', 1000000, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        artId = saveOpenArt("동시성 테스트 작품").getArtId();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void sameArtBidsAreSerializedAndLowerBidCannotOverwriteHigherPrice() throws Exception {
        CountDownLatch highBidReady = new CountDownLatch(1);
        CountDownLatch releaseHighBid = new CountDownLatch(1);
        CountDownLatch lowBidStarted = new CountDownLatch(1);

        Future<?> highBid = executor.submit(() ->
                transactionTemplate().executeWithoutResult(status -> {
                    artRepository.findByIdForUpdate(artId).orElseThrow();
                    bidService.createBid(artId, "bidder", createRequest(130_000));
                    highBidReady.countDown();
                    await(releaseHighBid);
                }));

        assertThat(highBidReady.await(5, TimeUnit.SECONDS)).isTrue();
        Future<BidCreateResponseDto> lowBid = executor.submit(() -> {
            lowBidStarted.countDown();
            return bidService.createBid(artId, "bidder", createRequest(120_000));
        });
        assertThat(lowBidStarted.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> lowBid.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseHighBid.countDown();
        }

        highBid.get(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> lowBid.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BidApiException.class)
                .satisfies(error -> {
                    BidApiException cause = (BidApiException) error.getCause();
                    assertThat(cause.getCode()).isEqualTo("BID_TOO_LOW");
                });

        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(130_000);
        assertThat(bidRepository.findAll())
                .singleElement()
                .satisfies(bid -> assertThat(bid.getBidPrice()).isEqualTo(130_000));
    }

    @Test
    void lockOnOneArtDoesNotBlockBidOnAnotherArt() throws Exception {
        Long otherArtId = saveOpenArt("다른 작품").getArtId();
        CountDownLatch lockReady = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Future<?> lockedArt = holdArtLock(artId, lockReady, releaseLock, false);
        assertThat(lockReady.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            BidCreateResponseDto response = executor
                    .submit(() -> bidService.createBid(
                            otherArtId,
                            "bidder",
                            createRequest(120_000)
                    ))
                    .get(2, TimeUnit.SECONDS);
            assertThat(response.getCurrentPrice()).isEqualTo(120_000);
        } finally {
            releaseLock.countDown();
        }

        lockedArt.get(5, TimeUnit.SECONDS);
        assertThat(artRepository.findById(otherArtId).orElseThrow().getCurrentPrice())
                .isEqualTo(120_000);
    }

    @Test
    void lockIsReleasedAfterSuccessfulTransaction() throws Exception {
        CountDownLatch lockReady = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> lockedArt = holdArtLock(artId, lockReady, releaseLock, false);
        assertThat(lockReady.await(5, TimeUnit.SECONDS)).isTrue();

        releaseLock.countDown();
        lockedArt.get(5, TimeUnit.SECONDS);

        BidCreateResponseDto response =
                bidService.createBid(artId, "bidder", createRequest(120_000));
        assertThat(response.getCurrentPrice()).isEqualTo(120_000);
    }

    @Test
    void lockIsReleasedAfterRolledBackTransaction() throws Exception {
        CountDownLatch lockReady = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> lockedArt = holdArtLock(artId, lockReady, releaseLock, true);
        assertThat(lockReady.await(5, TimeUnit.SECONDS)).isTrue();

        releaseLock.countDown();
        assertThatThrownBy(() -> lockedArt.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ForcedRollbackException.class);

        BidCreateResponseDto response =
                bidService.createBid(artId, "bidder", createRequest(120_000));
        assertThat(response.getCurrentPrice()).isEqualTo(120_000);
    }

    @Test
    void sameAccountCannotBeOvercommittedAcrossDifferentArts() throws Exception {
        Long otherArtId = saveOpenArt("같은 계정의 다른 작품").getArtId();
        jdbcTemplate.update(
                "UPDATE point_account SET available_balance = 150000 WHERE user_id = 'bidder'"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> {
            ready.countDown();
            await(start);
            return bidService.createBid(artId, "bidder", createRequest(120_000));
        });
        Future<?> second = executor.submit(() -> {
            ready.countDown();
            await(start);
            return bidService.createBid(otherArtId, "bidder", createRequest(130_000));
        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successCount = 0;
        int insufficientCount = 0;
        for (Future<?> result : java.util.List.of(first, second)) {
            try {
                result.get(5, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(BidApiException.class);
                assertThat(((BidApiException) exception.getCause()).getCode())
                        .isEqualTo("INSUFFICIENT_POINTS");
                insufficientCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(insufficientCount).isEqualTo(1);
        assertThat(bidRepository.count()).isEqualTo(1);
        assertThat(pointHoldRepository.count()).isEqualTo(1);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
        assertThat(pointAccountRepository.findById("bidder").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getHeldBalance()).isIn(120_000L, 130_000L);
                    assertThat(account.getAvailableBalance() + account.getHeldBalance())
                            .isEqualTo(150_000L);
                });
    }

    @Test
    void concurrentBidsAcrossManyArtsAndUsersPreserveEveryLedgerBalance() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        executor = Executors.newFixedThreadPool(16);

        List<Long> artIds = new ArrayList<>();
        artIds.add(artId);
        for (int index = 1; index < 4; index++) {
            artIds.add(saveOpenArt("부하 검증 작품 " + index).getArtId());
        }

        for (int index = 0; index < 16; index++) {
            String userId = "load" + index;
            userRepository.saveAndFlush(createUser(userId, 0));
            jdbcTemplate.update(
                    "update users set reserve = 200000 where user_id = ?",
                    userId
            );
            entityManager.clear();
            pointAccountService.initializeAccount(userId);
        }

        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            int bidderIndex = index;
            results.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                return bidService.createBid(
                        artIds.get(bidderIndex % artIds.size()),
                        "load" + bidderIndex,
                        createRequest(110_000 + bidderIndex * 1_000)
                );
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successCount = 0;
        for (Future<?> result : results) {
            try {
                result.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(BidApiException.class);
                assertThat(((BidApiException) exception.getCause()).getCode())
                        .isEqualTo("BID_TOO_LOW");
            }
        }

        assertThat(successCount).isGreaterThanOrEqualTo(4);
        assertThat(pointHoldRepository.findAll()
                .stream()
                .filter(hold -> hold.getStatus()
                        == com.dailyatelier.dailyatelier.entity.PointHoldStatus.HELD)
                .count()).isEqualTo(4);

        for (int index = 0; index < 16; index++) {
            String userId = "load" + index;
            var account = pointAccountRepository.findById(userId).orElseThrow();
            assertThat(account.getAvailableBalance())
                    .isEqualTo(pointTransactionRepository
                            .sumAvailableDeltaByUserId(userId));
            assertThat(account.getHeldBalance())
                    .isEqualTo(pointTransactionRepository
                            .sumHeldDeltaByUserId(userId));
            assertThat(account.getAvailableBalance() + account.getHeldBalance())
                    .isEqualTo(200_000L);
        }
    }

    private Future<?> holdArtLock(
            Long targetArtId,
            CountDownLatch lockReady,
            CountDownLatch releaseLock,
            boolean rollBack) {
        return executor.submit(() ->
                transactionTemplate().executeWithoutResult(status -> {
                    artRepository.findByIdForUpdate(targetArtId).orElseThrow();
                    lockReady.countDown();
                    await(releaseLock);
                    if (rollBack) {
                        throw new ForcedRollbackException();
                    }
                }));
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private Art saveOpenArt(String name) {
        LocalDateTime now = LocalDateTime.now();
        Art art = new Art();
        art.setArtist(seller);
        art.setName(name);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(now.minusDays(1));
        art.setClosingTime(now.plusDays(1));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(0);
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
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(userStatus);
        return user;
    }

    private BidCreateRequestDto createRequest(int bidPrice) {
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

    private static class ForcedRollbackException extends RuntimeException {
    }
}
