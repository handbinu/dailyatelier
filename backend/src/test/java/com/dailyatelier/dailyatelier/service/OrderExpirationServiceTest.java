package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-expiration-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        OrderService.class,
        ShippingAddressPolicy.class,
        OrderStateService.class,
        OrderExpirationService.class,
        OrderStateServiceTest.MutableClockConfig.class
})
class OrderExpirationServiceTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 28, 18, 0);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExpirationService expirationService;

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
    private OrderStateServiceTest.MutableClock clock;

    private Order order;

    @BeforeEach
    void setUp() {
        User buyer = userRepository.save(createUser("buyer", 0));
        User seller = userRepository.save(createUser("seller", 1));

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        Art art = new Art();
        art.setArtist(artist);
        art.setName("만료 테스트 작품");
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

        order = orderService.createForSoldAuction(art, bid, CREATED_AT);
    }

    @Test
    void doesNotExpireImmediatelyBeforeDeadline() {
        clock.setLocalDateTime(CREATED_AT.plusHours(24).minusNanos(1));

        assertThat(expirationService.expireOrder(order.getOrderId()))
                .isEqualTo(OrderExpirationResult.NOT_DUE);
        assertThat(orderRepository.findPaymentExpiredOrderIds(
                OrderStatus.PAYMENT_PENDING,
                CREATED_AT.plusHours(24).minusNanos(1),
                PageRequest.of(0, 10)
        )).isEmpty();
    }

    @Test
    void expiresAtExactDeadlineAndRepeatedCallIsIdempotent() {
        clock.setLocalDateTime(CREATED_AT.plusHours(24));

        assertThat(orderRepository.findPaymentExpiredOrderIds(
                OrderStatus.PAYMENT_PENDING,
                CREATED_AT.plusHours(24),
                PageRequest.of(0, 10)
        )).containsExactly(order.getOrderId());
        assertThat(expirationService.expireOrder(order.getOrderId()))
                .isEqualTo(OrderExpirationResult.EXPIRED);
        assertThat(expirationService.expireOrder(order.getOrderId()))
                .isEqualTo(OrderExpirationResult.ALREADY_PROCESSED);

        Order reloaded = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reloaded.getCanceledAt()).isEqualTo(CREATED_AT.plusHours(24));
        assertThat(reloaded.getCancelReason())
                .isEqualTo("PAYMENT_DEADLINE_EXPIRED");
    }

    @Test
    void expiresAfterDeadlineButSkipsPaidOrder() {
        clock.setLocalDateTime(CREATED_AT.plusHours(1));
        orderStateService.markPaid(order.getOrderId());
        clock.setLocalDateTime(CREATED_AT.plusHours(25));

        assertThat(expirationService.expireOrder(order.getOrderId()))
                .isEqualTo(OrderExpirationResult.ALREADY_PROCESSED);
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow()
                .getStatus()).isEqualTo(OrderStatus.PAID);
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
}
