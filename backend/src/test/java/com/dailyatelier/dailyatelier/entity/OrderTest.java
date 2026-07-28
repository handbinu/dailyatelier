package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 28, 12, 0);

    private User buyer;
    private User seller;
    private Artist artist;
    private Art art;
    private Bid winningBid;

    @BeforeEach
    void setUp() {
        buyer = createUser("buyer", "구매자", "구매닉네임", 0);
        seller = createUser("seller", "판매자", "판매닉네임", 1);

        artist = new Artist();
        artist.setArtistCode("artist-code");
        artist.setArtistName("테스트 작가");
        artist.setUser(seller);

        art = new Art();
        art.setArtId(1L);
        art.setArtist(artist);
        art.setName("테스트 작품");
        art.setImgPath("https://example.com/original.jpg");
        art.setArtStatus(Art.STATUS_SOLD);

        winningBid = new Bid();
        winningBid.setBidId(2L);
        winningBid.setArt(art);
        winningBid.setUser(buyer);
        winningBid.setBidPrice(150_000);
        art.setWinningBid(winningBid);
    }

    @Test
    void createsPaymentPendingOrderWithImmutableSnapshots() {
        OrderShippingAddress address = shippingAddress();
        Order order = createOrder(address);

        buyer.setName("변경된 구매자");
        buyer.setNickname("변경닉");
        seller.setName("변경된 판매자");
        artist.setArtistName("변경된 작가명");
        art.setName("변경된 작품명");
        art.setImgPath("https://example.com/changed.jpg");
        winningBid.setBidPrice(999_999);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getBuyerIdSnapshot()).isEqualTo("buyer");
        assertThat(order.getBuyerNameSnapshot()).isEqualTo("구매자");
        assertThat(order.getBuyerNicknameSnapshot()).isEqualTo("구매닉네임");
        assertThat(order.getSellerIdSnapshot()).isEqualTo("seller");
        assertThat(order.getSellerNameSnapshot()).isEqualTo("판매자");
        assertThat(order.getSellerArtistNameSnapshot()).isEqualTo("테스트 작가");
        assertThat(order.getArtIdSnapshot()).isEqualTo(1L);
        assertThat(order.getArtNameSnapshot()).isEqualTo("테스트 작품");
        assertThat(order.getArtImageSnapshot())
                .isEqualTo("https://example.com/original.jpg");
        assertThat(order.getWinningBidIdSnapshot()).isEqualTo(2L);
        assertThat(order.getWinningPrice()).isEqualTo(150_000);
        assertThat(order.getShippingAddress().getZipCode()).isEqualTo("02535");
        assertThat(order.getAddressConfirmedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void recordsNormalTransitionTimestampsAndRejectsSkippedTransition() {
        Order order = createOrder(shippingAddress());

        assertThatThrownBy(() -> order.transitionTo(
                OrderStatus.SHIPPED,
                CREATED_AT.plusHours(1),
                null
        )).isInstanceOf(IllegalStateException.class);

        order.transitionTo(OrderStatus.PAID, CREATED_AT.plusHours(1), null);
        order.transitionTo(OrderStatus.PREPARING, CREATED_AT.plusHours(2), null);
        order.transitionTo(OrderStatus.SHIPPED, CREATED_AT.plusHours(3), null);
        order.transitionTo(OrderStatus.DELIVERED, CREATED_AT.plusHours(4), null);
        order.transitionTo(OrderStatus.CONFIRMED, CREATED_AT.plusHours(5), null);

        assertThat(order.getPaidAt()).isEqualTo(CREATED_AT.plusHours(1));
        assertThat(order.getPreparingAt()).isEqualTo(CREATED_AT.plusHours(2));
        assertThat(order.getShippedAt()).isEqualTo(CREATED_AT.plusHours(3));
        assertThat(order.getDeliveredAt()).isEqualTo(CREATED_AT.plusHours(4));
        assertThat(order.getConfirmedAt()).isEqualTo(CREATED_AT.plusHours(5));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus().isTerminal()).isTrue();
    }

    @Test
    void requiresReasonForCancellationAndRefund() {
        Order canceledOrder = createOrder(null);

        assertThatThrownBy(() -> canceledOrder.transitionTo(
                OrderStatus.CANCELED,
                CREATED_AT.plusHours(1),
                " "
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(canceledOrder.getCanceledAt()).isNull();
        assertThat(canceledOrder.getCancelReason()).isNull();

        canceledOrder.transitionTo(
                OrderStatus.CANCELED,
                CREATED_AT.plusHours(1),
                "결제 기한 만료"
        );

        Order refundedOrder = createOrder(shippingAddress());
        refundedOrder.transitionTo(
                OrderStatus.PAID,
                CREATED_AT.plusMinutes(10),
                null
        );
        refundedOrder.transitionTo(
                OrderStatus.REFUNDED,
                CREATED_AT.plusMinutes(20),
                "결제 취소 완료"
        );

        assertThat(canceledOrder.getCanceledAt())
                .isEqualTo(CREATED_AT.plusHours(1));
        assertThat(canceledOrder.getCancelReason()).isEqualTo("결제 기한 만료");
        assertThat(refundedOrder.getRefundedAt())
                .isEqualTo(CREATED_AT.plusMinutes(20));
        assertThat(refundedOrder.getRefundReason()).isEqualTo("결제 취소 완료");
    }

    @Test
    void reconfirmsAddressWhilePaymentPendingAndBlocksAfterPayment() {
        Order order = createOrder(null);

        order.confirmShippingAddress(
                shippingAddress(),
                CREATED_AT.plusMinutes(5)
        );

        assertThat(order.getShippingAddress().getZipCode()).isEqualTo("02535");
        assertThat(order.getAddressConfirmedAt())
                .isEqualTo(CREATED_AT.plusMinutes(5));

        OrderShippingAddress changedAddress = OrderShippingAddress.of(
                "변경 수령인",
                "010-9999-8888",
                "12345",
                "부산광역시 해운대구",
                "202호"
        );
        order.confirmShippingAddress(
                changedAddress,
                CREATED_AT.plusMinutes(6)
        );

        assertThat(order.getShippingAddress().getZipCode()).isEqualTo("12345");
        assertThat(order.getShippingAddress().getRecipientName())
                .isEqualTo("변경 수령인");
        assertThat(order.getAddressConfirmedAt())
                .isEqualTo(CREATED_AT.plusMinutes(6));

        order.transitionTo(OrderStatus.PAID, CREATED_AT.plusMinutes(10), null);
        assertThatThrownBy(() -> order.confirmShippingAddress(
                shippingAddress(),
                CREATED_AT.plusMinutes(11)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 대기 주문");
    }

    @Test
    void rejectsUnsoldArtAndMismatchedWinner() {
        art.setArtStatus(Art.STATUS_UNSOLD);
        assertThatThrownBy(() -> createOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("낙찰된 작품");

        art.setArtStatus(Art.STATUS_SOLD);
        User otherBuyer = createUser("other", "다른 구매자", "다른닉네임", 0);
        assertThatThrownBy(() -> Order.create(
                art,
                winningBid,
                otherBuyer,
                seller,
                CREATED_AT,
                CREATED_AT.plusHours(24),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");
    }

    private Order createOrder(OrderShippingAddress address) {
        return Order.create(
                art,
                winningBid,
                buyer,
                seller,
                CREATED_AT,
                CREATED_AT.plusHours(24),
                address
        );
    }

    private OrderShippingAddress shippingAddress() {
        return OrderShippingAddress.of(
                "구매자",
                "010-1111-2222",
                "02535",
                "서울특별시 중랑구",
                "101호"
        );
    }

    private User createUser(
            String userId,
            String name,
            String nickname,
            int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setName(name);
        user.setNickname(nickname);
        user.setPhoneNumber("010-0000-0000");
        user.setUserStatus(userStatus);
        return user;
    }
}
