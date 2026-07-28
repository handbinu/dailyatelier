package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
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
        "spring.datasource.url=jdbc:h2:mem:order-state-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        OrderService.class,
        ShippingAddressPolicy.class,
        OrderStateService.class,
        OrderExpirationService.class,
        OrderStateConcurrencyTest.MutableClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderStateConcurrencyTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 28, 18, 0);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderStateService orderStateService;

    @Autowired
    private OrderExpirationService expirationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AddressRepository addressRepository;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private ExecutorService executor;
    private Long orderId;

    @BeforeEach
    void setUp() {
        resetDatabase();
        clock.setLocalDateTime(CREATED_AT.plusHours(1));
        orderId = transactionTemplate().execute(status -> createOrder());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void paymentCommittedFirstMakesExpirationIdempotentlySkip() throws Exception {
        CountDownLatch paymentChanged = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);

        Future<Order> paymentFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    Order paid = orderStateService.markPaid(orderId);
                    paymentChanged.countDown();
                    await(releasePayment);
                    return paid;
                }));
        assertThat(paymentChanged.await(5, TimeUnit.SECONDS)).isTrue();

        clock.setLocalDateTime(CREATED_AT.plusHours(24));
        Future<OrderExpirationResult> expirationFuture =
                executor.submit(() -> expirationService.expireOrder(orderId));

        try {
            assertThatThrownBy(() ->
                    expirationFuture.get(300, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);
        } finally {
            releasePayment.countDown();
        }

        paymentFuture.get(5, TimeUnit.SECONDS);
        assertThat(expirationFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(OrderExpirationResult.ALREADY_PROCESSED);
        assertThat(reload().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void expirationCommittedFirstRejectsWaitingPayment() throws Exception {
        clock.setLocalDateTime(CREATED_AT.plusHours(24));
        CountDownLatch expirationChanged = new CountDownLatch(1);
        CountDownLatch releaseExpiration = new CountDownLatch(1);

        Future<OrderExpirationResult> expirationFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    OrderExpirationResult result =
                            expirationService.expireOrder(orderId);
                    expirationChanged.countDown();
                    await(releaseExpiration);
                    return result;
                }));
        assertThat(expirationChanged.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Order> paymentFuture =
                executor.submit(() -> orderStateService.markPaid(orderId));

        try {
            assertThatThrownBy(() ->
                    paymentFuture.get(300, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);
        } finally {
            releaseExpiration.countDown();
        }

        assertThat(expirationFuture.get(5, TimeUnit.SECONDS))
                .isEqualTo(OrderExpirationResult.EXPIRED);
        assertThatThrownBy(() -> paymentFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OrderApiException.class);
        assertThat(reload().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reload().getCancelReason())
                .isEqualTo("PAYMENT_DEADLINE_EXPIRED");
    }

    @Test
    void buyerForfeitCommittedFirstRejectsWaitingPayment() throws Exception {
        CountDownLatch cancelChanged = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);

        Future<Order> cancelFuture = executor.submit(() ->
                transactionTemplate().execute(status -> {
                    Order canceled =
                            orderStateService.cancelPending(orderId, "buyer");
                    cancelChanged.countDown();
                    await(releaseCancel);
                    return canceled;
                }));
        assertThat(cancelChanged.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Order> paymentFuture =
                executor.submit(() -> orderStateService.markPaid(orderId));

        try {
            assertThatThrownBy(() ->
                    paymentFuture.get(300, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);
        } finally {
            releaseCancel.countDown();
        }

        cancelFuture.get(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> paymentFuture.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OrderApiException.class);
        assertThat(reload().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reload().getCancelReason()).isEqualTo("BUYER_FORFEIT");
    }

    private Long createOrder() {
        User buyer = userRepository.save(createUser("buyer", 0));
        User seller = createUser("seller", 1);

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        Art art = new Art();
        art.setArtist(artist);
        art.setName("경합 테스트 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(150_000);
        art.setBidStartTime(CREATED_AT.minusDays(1));
        art.setClosingTime(CREATED_AT);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);

        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(buyer);
        bid.setBidPrice(150_000);
        bid.setBidTime(CREATED_AT.minusMinutes(1));
        bid = bidRepository.save(bid);

        art.setWinningBid(bid);
        art.setClosedAt(CREATED_AT);
        art.setArtStatus(Art.STATUS_SOLD);
        artRepository.save(art);

        Address address = new Address();
        address.setUser(buyer);
        address.setZipCode("02535");
        address.setUserAddress1("서울특별시 중랑구");
        addressRepository.save(address);

        return orderService.createForSoldAuction(
                art,
                bid,
                CREATED_AT
        ).getOrderId();
    }

    private Order reload() {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(userId);
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(CREATED_AT.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }

    private void resetDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.execute("TRUNCATE TABLE address");
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
                    CREATED_AT.atZone(ZONE_ID).toInstant(),
                    ZONE_ID
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

        void setLocalDateTime(LocalDateTime dateTime) {
            instant.set(dateTime.atZone(zone).toInstant());
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
