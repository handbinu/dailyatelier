package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressResponseDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        OrderService.class,
        ShippingAddressPolicy.class,
        OrderServiceTest.FixedClockConfig.class
})
class OrderServiceTest {
    private static final LocalDateTime CLOSED_AT =
            LocalDateTime.of(2026, 7, 28, 18, 0);

    @Autowired
    private OrderService orderService;

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

    private User buyer;
    private User otherBuyer;
    private User seller;
    private Art art;
    private Bid winningBid;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(createUser("buyer", "구매자", 0));
        otherBuyer = userRepository.save(createUser("other", "다른 구매자", 0));
        seller = userRepository.save(createUser("seller", "판매자", 1));

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        art = new Art();
        art.setArtist(artist);
        art.setName("낙찰 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(150_000);
        art.setBidStartTime(CLOSED_AT.minusDays(1));
        art.setClosingTime(CLOSED_AT);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);

        winningBid = new Bid();
        winningBid.setArt(art);
        winningBid.setUser(buyer);
        winningBid.setBidPrice(150_000);
        winningBid.setBidTime(CLOSED_AT.minusMinutes(1));
        winningBid = bidRepository.save(winningBid);

        art.setWinningBid(winningBid);
        art.setClosedAt(CLOSED_AT);
        art.setArtStatus(Art.STATUS_SOLD);
        art = artRepository.save(art);
    }

    @Test
    void createsOrderWithCompleteDefaultAddressAndFixedDeadline() {
        saveDefaultAddress("02535", "서울특별시 중랑구", "101호");

        Order order = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getShippingAddress()).isNotNull();
        assertThat(order.getShippingAddress().getRecipientName()).isEqualTo("구매자");
        assertThat(order.getShippingAddress().getZipCode()).isEqualTo("02535");
        assertThat(order.getAddressConfirmedAt()).isEqualTo(CLOSED_AT);
        assertThat(order.getPaymentDueAt()).isEqualTo(CLOSED_AT.plusHours(24));
    }

    @Test
    void createsAddressRequiredOrderForMissingOrIncompleteDefaultAddress() {
        saveDefaultAddress("1234", " ", "101호");

        Order order = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getShippingAddress()).isNull();
        assertThat(order.getAddressConfirmedAt()).isNull();
    }

    @Test
    void createsAddressRequiredOrderWhenDefaultAddressDoesNotExist() {
        Order order = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );

        assertThat(order.getShippingAddress()).isNull();
        assertThat(order.getAddressConfirmedAt()).isNull();
        assertThat(order.getPaymentDueAt()).isEqualTo(CLOSED_AT.plusHours(24));
    }

    @Test
    void repeatedCreationReturnsSameOrder() {
        Order first = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );
        Order second = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT.plusMinutes(1)
        );

        assertThat(second.getOrderId()).isEqualTo(first.getOrderId());
        assertThat(second.getCreatedAt()).isEqualTo(CLOSED_AT);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void buyerCanReconfirmAddressAndOptionallySaveItAsDefault() {
        Order order = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );
        OrderShippingAddressRequestDto firstRequest = shippingRequest(
                "02535",
                "서울특별시 중랑구",
                false
        );

        OrderShippingAddressResponseDto first =
                orderService.confirmShippingAddress(
                        order.getOrderId(),
                        buyer.getUserId(),
                        firstRequest
                );

        assertThat(first.getZipCode()).isEqualTo("02535");
        assertThat(addressRepository.findById(buyer.getUserId())).isEmpty();

        OrderShippingAddressRequestDto changedRequest = shippingRequest(
                "48000",
                "부산광역시 해운대구",
                true
        );
        OrderShippingAddressResponseDto changed =
                orderService.confirmShippingAddress(
                        order.getOrderId(),
                        buyer.getUserId(),
                        changedRequest
                );

        assertThat(changed.getZipCode()).isEqualTo("48000");
        assertThat(changed.getAddress1()).isEqualTo("부산광역시 해운대구");
        assertThat(changed.getAddressConfirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 19, 0));
        assertThat(addressRepository.findById(buyer.getUserId()))
                .hasValueSatisfying(address -> {
                    assertThat(address.getZipCode()).isEqualTo("48000");
                    assertThat(address.getUserAddress1())
                            .isEqualTo("부산광역시 해운대구");
                });
    }

    @Test
    void blocksOtherBuyerAndAddressChangeAfterPayment() {
        Order order = orderService.createForSoldAuction(
                art,
                winningBid,
                CLOSED_AT
        );
        OrderShippingAddressRequestDto request = shippingRequest(
                "02535",
                "서울특별시 중랑구",
                false
        );

        assertThatThrownBy(() -> orderService.confirmShippingAddress(
                order.getOrderId(),
                otherBuyer.getUserId(),
                request
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_ACCESS_DENIED"));

        order.confirmShippingAddress(
                new ShippingAddressPolicy().fromRequest(request),
                CLOSED_AT.plusMinutes(1)
        );
        order.transitionTo(OrderStatus.PAID, CLOSED_AT.plusMinutes(2), null);
        orderRepository.saveAndFlush(order);

        assertThatThrownBy(() -> orderService.confirmShippingAddress(
                order.getOrderId(),
                buyer.getUserId(),
                request
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("SHIPPING_ADDRESS_LOCKED"));
    }

    private void saveDefaultAddress(
            String zipCode,
            String address1,
            String address2) {
        Address address = new Address();
        address.setUser(buyer);
        address.setZipCode(zipCode);
        address.setUserAddress1(address1);
        address.setUserAddress2(address2);
        addressRepository.save(address);
    }

    private OrderShippingAddressRequestDto shippingRequest(
            String zipCode,
            String address1,
            boolean saveAsDefault) {
        OrderShippingAddressRequestDto request =
                new OrderShippingAddressRequestDto();
        request.setRecipientName("구매자");
        request.setRecipientPhone("010-0000-0000");
        request.setZipCode(zipCode);
        request.setAddress1(address1);
        request.setAddress2("202호");
        request.setSaveAsDefault(saveAsDefault);
        return request;
    }

    private User createUser(String userId, String name, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(name);
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
                    Instant.parse("2026-07-28T10:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
