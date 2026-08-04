package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.OrderAction;
import com.dailyatelier.dailyatelier.dto.OrderDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderPageResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-query-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(OrderQueryService.class)
class OrderQueryServiceTest {
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 7, 28, 18, 0);

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderRepository orderRepository;

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
    private User otherSeller;
    private Artist artist;
    private Artist otherArtist;
    private Order olderPendingOrder;
    private Order newerPaidOrder;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(createUser("buyer", 0));
        otherBuyer = userRepository.save(createUser("buyer2", 0));
        seller = userRepository.save(createUser("seller", 1));
        otherSeller = userRepository.save(createUser("seller2", 1));
        artist = saveArtist(seller, "판매 작가");
        otherArtist = saveArtist(otherSeller, "다른 작가");

        olderPendingOrder = saveOrder(
                "older",
                buyer,
                seller,
                artist,
                BASE_TIME
        );
        newerPaidOrder = saveOrder(
                "newer",
                buyer,
                seller,
                artist,
                BASE_TIME.plusMinutes(1)
        );
        newerPaidOrder.transitionTo(
                OrderStatus.PAID,
                BASE_TIME.plusMinutes(2),
                null
        );
        orderRepository.save(newerPaidOrder);

        saveOrder(
                "otherBuyer",
                otherBuyer,
                seller,
                artist,
                BASE_TIME.plusMinutes(2)
        );
        saveOrder(
                "otherSeller",
                buyer,
                otherSeller,
                otherArtist,
                BASE_TIME.plusMinutes(3)
        );
    }

    @Test
    void buyerListFiltersSortsPagesAndCountsAllStatuses() {
        OrderPageResponseDto firstPage = orderQueryService.getBuyerOrders(
                buyer.getUserId(),
                null,
                0,
                1
        );
        OrderPageResponseDto paid = orderQueryService.getBuyerOrders(
                buyer.getUserId(),
                OrderStatus.PAID,
                0,
                12
        );
        OrderPageResponseDto outOfRange = orderQueryService.getBuyerOrders(
                buyer.getUserId(),
                null,
                10,
                12
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getOrderId()).isNotNull();
                    assertThat(result.getOrderNumber()).startsWith("ORD-");
                    assertThat(result.getArtName()).isEqualTo("otherSeller 작품");
                    assertThat(result.getCounterpartyName()).isEqualTo("다른 작가");
                });
        assertThat(firstPage.getStatusCounts())
                .containsEntry(OrderStatus.PAYMENT_PENDING, 2L)
                .containsEntry(OrderStatus.PAID, 1L)
                .containsEntry(OrderStatus.CANCELED, 0L);
        assertThat(paid.getContent())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getOrderId())
                            .isEqualTo(newerPaidOrder.getOrderId());
                    assertThat(result.getAvailableActions())
                            .containsExactly(com.dailyatelier.dailyatelier.dto.OrderAction.REQUEST_REFUND);
                });
        assertThat(outOfRange.getContent()).isEmpty();
        assertThat(outOfRange.getTotalElements()).isEqualTo(3);
    }

    @Test
    void sellerListUsesBuyerAsCounterpartyAndFiltersOwnership() {
        OrderPageResponseDto result = orderQueryService.getSellerOrders(
                seller.getUserId(),
                OrderStatus.PAYMENT_PENDING,
                0,
                12
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting("counterpartyName")
                .containsExactly("buyer2", "buyer");
        assertThat(result.getContent())
                .allSatisfy(item ->
                        assertThat(item.getAvailableActions()).isEmpty());
    }

    @Test
    void detailsExposeSnapshotsAndRoleSpecificActionsWithoutRawUserIds() {
        OrderDetailResponseDto buyerDetail = orderQueryService.getBuyerOrder(
                buyer.getUserId(),
                olderPendingOrder.getOrderId()
        );
        OrderDetailResponseDto sellerDetail = orderQueryService.getSellerOrder(
                seller.getUserId(),
                newerPaidOrder.getOrderId()
        );

        assertThat(buyerDetail.getBuyerName()).isEqualTo("buyer");
        assertThat(buyerDetail.getSellerArtistName()).isEqualTo("판매 작가");
        assertThat(buyerDetail.getShippingAddress().getZipCode())
                .isEqualTo("02535");
        assertThat(buyerDetail.getAvailableActions()).containsExactly(
                OrderAction.UPDATE_SHIPPING_ADDRESS,
                OrderAction.CANCEL
        );
        assertThat(sellerDetail.getBuyerNickname()).isEqualTo("buyer");
        assertThat(sellerDetail.getAvailableActions())
                .containsExactly(OrderAction.START_PREPARING);
    }

    @Test
    void approvedRefundIsConsistentAcrossBuyerAndSellerViews() {
        newerPaidOrder.requestRefund("작품 상태 문제", BASE_TIME.plusMinutes(3));
        newerPaidOrder.approveRefund();
        newerPaidOrder.transitionTo(
                OrderStatus.REFUNDED,
                BASE_TIME.plusMinutes(4),
                "PAYMENT_CANCELED"
        );
        orderRepository.saveAndFlush(newerPaidOrder);

        OrderPageResponseDto buyerList = orderQueryService.getBuyerOrders(
                buyer.getUserId(), OrderStatus.REFUNDED, 0, 12);
        OrderPageResponseDto sellerList = orderQueryService.getSellerOrders(
                seller.getUserId(), OrderStatus.REFUNDED, 0, 12);
        OrderDetailResponseDto buyerDetail = orderQueryService.getBuyerOrder(
                buyer.getUserId(), newerPaidOrder.getOrderId());
        OrderDetailResponseDto sellerDetail = orderQueryService.getSellerOrder(
                seller.getUserId(), newerPaidOrder.getOrderId());

        assertThat(buyerList.getContent()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(result.getRefundRequestStatus())
                    .isEqualTo(OrderRefundRequestStatus.APPROVED);
            assertThat(result.getAvailableActions()).isEmpty();
        });
        assertThat(sellerList.getContent()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(result.getRefundRequestStatus())
                    .isEqualTo(OrderRefundRequestStatus.APPROVED);
            assertThat(result.getAvailableActions()).isEmpty();
        });
        assertThat(buyerDetail.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(buyerDetail.getRefundRequestStatus())
                .isEqualTo(OrderRefundRequestStatus.APPROVED);
        assertThat(buyerDetail.getAvailableActions()).isEmpty();
        assertThat(sellerDetail.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(sellerDetail.getRefundRequestStatus())
                .isEqualTo(OrderRefundRequestStatus.APPROVED);
        assertThat(sellerDetail.getAvailableActions()).isEmpty();
    }

    @Test
    void rejectsOtherPartyAndInvalidPageRequest() {
        assertThatThrownBy(() -> orderQueryService.getBuyerOrder(
                otherBuyer.getUserId(),
                olderPendingOrder.getOrderId()
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_ACCESS_DENIED"));

        assertThatThrownBy(() -> orderQueryService.getSellerOrder(
                otherSeller.getUserId(),
                olderPendingOrder.getOrderId()
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("ORDER_ACCESS_DENIED"));

        assertThatThrownBy(() -> orderQueryService.getBuyerOrders(
                buyer.getUserId(),
                null,
                -1,
                12
        )).isInstanceOf(OrderApiException.class)
                .satisfies(error -> assertThat(
                        ((OrderApiException) error).getCode()
                ).isEqualTo("INVALID_PAGE_REQUEST"));
    }

    private Order saveOrder(
            String suffix,
            User orderBuyer,
            User orderSeller,
            Artist orderArtist,
            LocalDateTime createdAt) {
        Art art = new Art();
        art.setArtist(orderArtist);
        art.setName(suffix + " 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(150_000);
        art.setBidStartTime(createdAt.minusDays(1));
        art.setClosingTime(createdAt);
        art.setImgPath("https://example.com/" + suffix + ".jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);

        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(orderBuyer);
        bid.setBidPrice(150_000);
        bid.setBidTime(createdAt.minusMinutes(1));
        bid = bidRepository.save(bid);

        art.setWinningBid(bid);
        art.setClosedAt(createdAt);
        art.setArtStatus(Art.STATUS_SOLD);
        artRepository.save(art);

        return orderRepository.save(Order.create(
                art,
                bid,
                orderBuyer,
                orderSeller,
                createdAt,
                createdAt.plusHours(24),
                OrderShippingAddress.of(
                        orderBuyer.getName(),
                        orderBuyer.getPhoneNumber(),
                        "02535",
                        "서울특별시 중랑구",
                        null
                )
        ));
    }

    private Artist saveArtist(User user, String artistName) {
        Artist savedArtist = new Artist();
        savedArtist.setUser(user);
        savedArtist.setArtistName(artistName);
        return artistRepository.save(savedArtist);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(userId);
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(BASE_TIME.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }
}
