package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderRefundReason;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-state-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        OrderService.class,
        ShippingAddressPolicy.class,
        OrderStateService.class,
        OrderStateServiceTest.MutableClockConfig.class
})
class OrderStateServiceTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 28, 18, 0);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderStateService orderStateService;

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
    private MutableClock clock;

    private User buyer;
    private User seller;
    private Order order;

    @BeforeEach
    void setUp() {
        clock.setLocalDateTime(CREATED_AT.plusHours(1));
        buyer = userRepository.save(createUser("buyer", 0));
        seller = userRepository.save(createUser("seller", 1));
        User other = userRepository.save(createUser("other", 0));
        assertThat(other.getUserId()).isEqualTo("other");

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        Art art = new Art();
        art.setArtist(artist);
        art.setName("상태 테스트 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(150_000);
        art.setBidStartTime(CREATED_AT.minusDays(1));
        art.setClosingTime(CREATED_AT);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);

        Bid winningBid = new Bid();
        winningBid.setArt(art);
        winningBid.setUser(buyer);
        winningBid.setBidPrice(150_000);
        winningBid.setBidTime(CREATED_AT.minusMinutes(1));
        winningBid = bidRepository.save(winningBid);

        art.setWinningBid(winningBid);
        art.setClosedAt(CREATED_AT);
        art.setArtStatus(Art.STATUS_SOLD);
        artRepository.save(art);

        Address address = new Address();
        address.setUser(buyer);
        address.setZipCode("02535");
        address.setUserAddress1("서울특별시 중랑구");
        address.setUserAddress2("101호");
        addressRepository.save(address);

        order = orderService.createForSoldAuction(
                art,
                winningBid,
                CREATED_AT
        );
    }

    @Test
    void processesNormalFlowAndRecordsTimestampsAndShipment() {
        clock.setLocalDateTime(CREATED_AT.plusHours(1));
        orderStateService.markPaid(order.getOrderId());
        clock.setLocalDateTime(CREATED_AT.plusHours(2));
        orderStateService.startPreparing(order.getOrderId(), seller.getUserId());
        clock.setLocalDateTime(CREATED_AT.plusHours(3));
        orderStateService.ship(
                order.getOrderId(),
                seller.getUserId(),
                "  우체국택배 ",
                " 1234-5678 "
        );
        clock.setLocalDateTime(CREATED_AT.plusHours(4));
        orderStateService.markDelivered(order.getOrderId());
        clock.setLocalDateTime(CREATED_AT.plusHours(5));
        orderStateService.confirm(order.getOrderId(), buyer.getUserId());

        Order reloaded = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloaded.getPaidAt()).isEqualTo(CREATED_AT.plusHours(1));
        assertThat(reloaded.getPreparingAt()).isEqualTo(CREATED_AT.plusHours(2));
        assertThat(reloaded.getShippedAt()).isEqualTo(CREATED_AT.plusHours(3));
        assertThat(reloaded.getDeliveredAt()).isEqualTo(CREATED_AT.plusHours(4));
        assertThat(reloaded.getConfirmedAt()).isEqualTo(CREATED_AT.plusHours(5));
        assertThat(reloaded.getShippingCarrier()).isEqualTo("우체국택배");
        assertThat(reloaded.getTrackingNumber()).isEqualTo("1234-5678");
    }

    @Test
    void validatesSellerRoleAndShipmentBeforeMutation() {
        orderStateService.markPaid(order.getOrderId());

        assertThatThrownBy(() -> orderStateService.startPreparing(
                order.getOrderId(),
                "other"
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_ACCESS_DENIED"));

        orderStateService.startPreparing(order.getOrderId(), seller.getUserId());
        assertThatThrownBy(() -> orderStateService.ship(
                order.getOrderId(),
                seller.getUserId(),
                " ",
                "1234"
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("INVALID_SHIPPING_INFO"));

        Order reloaded = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(reloaded.getShippedAt()).isNull();
        assertThat(reloaded.getShippingCarrier()).isNull();
        assertThat(reloaded.getTrackingNumber()).isNull();
    }

    @Test
    void distinguishesBuyerForfeitFromRefund() {
        Order canceled = orderStateService.cancelPending(
                order.getOrderId(),
                buyer.getUserId()
        );

        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getCancelReason()).isEqualTo("BUYER_FORFEIT");
        assertThat(canceled.getRefundReason()).isNull();
        assertThatThrownBy(() -> orderStateService.markPaid(order.getOrderId()))
                .isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_STATUS_CONFLICT"));
    }

    @Test
    void refundsOnlyPaidOrPreparingOrderWithExplicitReason() {
        orderStateService.markPaid(order.getOrderId());
        Order refunded = orderStateService.refund(
                order.getOrderId(),
                OrderRefundReason.PAYMENT_CANCELED
        );

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.getRefundReason()).isEqualTo("PAYMENT_CANCELED");
        assertThat(refunded.getRefundedAt()).isEqualTo(CREATED_AT.plusHours(1));
        assertThat(refunded.getCancelReason()).isNull();
        assertThatThrownBy(() -> orderStateService.startPreparing(
                order.getOrderId(),
                seller.getUserId()
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_STATUS_CONFLICT"));
    }

    @Test
    void rejectsPaymentAtOrAfterDeadline() {
        clock.setLocalDateTime(CREATED_AT.plusHours(24));

        assertThatThrownBy(() -> orderStateService.markPaid(order.getOrderId()))
                .isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("PAYMENT_DEADLINE_EXPIRED"));
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow()
                .getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
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
