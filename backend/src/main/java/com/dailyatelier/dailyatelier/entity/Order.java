package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_orders_art",
                columnNames = "art_id"
        ),
        indexes = {
                @Index(
                        name = "idx_orders_buyer_status_created",
                        columnList = "buyer_id, status, created_at"
                ),
                @Index(
                        name = "idx_orders_seller_status_created",
                        columnList = "seller_id, status, created_at"
                ),
                @Index(
                        name = "idx_orders_payment_expiration",
                        columnList = "status, payment_due_at, order_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "art_id", nullable = false, unique = true)
    private Art art;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winning_bid_id", nullable = false)
    private Bid winningBid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "buyer_id_snapshot", nullable = false, length = 45)
    private String buyerIdSnapshot;

    @Column(name = "buyer_name_snapshot", nullable = false, length = 50)
    private String buyerNameSnapshot;

    @Column(name = "buyer_nickname_snapshot", nullable = false, length = 10)
    private String buyerNicknameSnapshot;

    @Column(name = "buyer_phone_snapshot", nullable = false, length = 30)
    private String buyerPhoneSnapshot;

    @Column(name = "seller_id_snapshot", nullable = false, length = 45)
    private String sellerIdSnapshot;

    @Column(name = "seller_name_snapshot", nullable = false, length = 50)
    private String sellerNameSnapshot;

    @Column(name = "seller_nickname_snapshot", nullable = false, length = 10)
    private String sellerNicknameSnapshot;

    @Column(name = "seller_artist_name_snapshot", nullable = false, length = 50)
    private String sellerArtistNameSnapshot;

    @Column(name = "seller_phone_snapshot", nullable = false, length = 30)
    private String sellerPhoneSnapshot;

    @Column(name = "art_id_snapshot", nullable = false)
    private Long artIdSnapshot;

    @Column(name = "art_name_snapshot", nullable = false, length = 30)
    private String artNameSnapshot;

    @Column(name = "art_image_snapshot", nullable = false, length = 500)
    private String artImageSnapshot;

    @Column(name = "winning_bid_id_snapshot", nullable = false)
    private Long winningBidIdSnapshot;

    @Column(name = "winning_price", nullable = false)
    private Integer winningPrice;

    @Embedded
    private OrderShippingAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "payment_due_at", nullable = false)
    private LocalDateTime paymentDueAt;

    @Column(name = "address_confirmed_at")
    private LocalDateTime addressConfirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "preparing_at")
    private LocalDateTime preparingAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "refund_reason", length = 200)
    private String refundReason;

    @Column(name = "shipping_carrier", length = 50)
    private String shippingCarrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    public static Order create(
            Art art,
            Bid winningBid,
            User buyer,
            User seller,
            LocalDateTime createdAt,
            LocalDateTime paymentDueAt,
            OrderShippingAddress shippingAddress) {
        validateCreation(
                art,
                winningBid,
                buyer,
                seller,
                createdAt,
                paymentDueAt
        );

        Artist artist = art.getArtist();
        Order order = new Order();
        order.art = art;
        order.winningBid = winningBid;
        order.buyer = buyer;
        order.seller = seller;
        order.buyerIdSnapshot = buyer.getUserId();
        order.buyerNameSnapshot = buyer.getName();
        order.buyerNicknameSnapshot = buyer.getNickname();
        order.buyerPhoneSnapshot = buyer.getPhoneNumber();
        order.sellerIdSnapshot = seller.getUserId();
        order.sellerNameSnapshot = seller.getName();
        order.sellerNicknameSnapshot = seller.getNickname();
        order.sellerArtistNameSnapshot = requireText(
                artist.getArtistName(),
                "판매자 작가명"
        );
        order.sellerPhoneSnapshot = seller.getPhoneNumber();
        order.artIdSnapshot = art.getArtId();
        order.artNameSnapshot = art.getName();
        order.artImageSnapshot = art.getImgPath();
        order.winningBidIdSnapshot = winningBid.getBidId();
        order.winningPrice = winningBid.getBidPrice();
        order.shippingAddress = shippingAddress;
        order.status = OrderStatus.PAYMENT_PENDING;
        order.createdAt = createdAt;
        order.paymentDueAt = paymentDueAt;
        order.addressConfirmedAt = shippingAddress == null ? null : createdAt;
        return order;
    }

    public void confirmShippingAddress(
            OrderShippingAddress address,
            LocalDateTime confirmedAt) {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "결제 대기 주문에서만 배송지를 확정할 수 있습니다"
            );
        }
        this.shippingAddress = Objects.requireNonNull(
                address,
                "배송지는 필수입니다"
        );
        this.addressConfirmedAt = Objects.requireNonNull(
                confirmedAt,
                "배송지 확정 시각은 필수입니다"
        );
    }

    public void transitionTo(
            OrderStatus nextStatus,
            LocalDateTime transitionedAt,
            String reason) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException(
                    status + "에서 " + nextStatus + "(으)로 변경할 수 없습니다"
            );
        }
        Objects.requireNonNull(transitionedAt, "상태 변경 시각은 필수입니다");

        switch (nextStatus) {
            case PAID -> paidAt = transitionedAt;
            case PREPARING -> preparingAt = transitionedAt;
            case SHIPPED -> shippedAt = transitionedAt;
            case DELIVERED -> deliveredAt = transitionedAt;
            case CONFIRMED -> confirmedAt = transitionedAt;
            case CANCELED -> {
                String normalizedReason = requireReason(reason, "취소");
                canceledAt = transitionedAt;
                cancelReason = normalizedReason;
            }
            case REFUNDED -> {
                String normalizedReason = requireReason(reason, "환불");
                refundedAt = transitionedAt;
                refundReason = normalizedReason;
            }
            case PAYMENT_PENDING ->
                    throw new IllegalStateException("결제 대기 상태로 되돌릴 수 없습니다");
        }
        status = nextStatus;
    }

    private static void validateCreation(
            Art art,
            Bid winningBid,
            User buyer,
            User seller,
            LocalDateTime createdAt,
            LocalDateTime paymentDueAt) {
        Objects.requireNonNull(art, "작품은 필수입니다");
        Objects.requireNonNull(winningBid, "낙찰 입찰은 필수입니다");
        Objects.requireNonNull(buyer, "구매자는 필수입니다");
        Objects.requireNonNull(seller, "판매자는 필수입니다");
        Objects.requireNonNull(createdAt, "주문 생성 시각은 필수입니다");
        Objects.requireNonNull(paymentDueAt, "결제 기한은 필수입니다");

        if (art.getArtId() == null || winningBid.getBidId() == null) {
            throw new IllegalArgumentException(
                    "저장된 작품과 낙찰 입찰로만 주문을 생성할 수 있습니다"
            );
        }
        if (art.getArtStatus() != Art.STATUS_SOLD) {
            throw new IllegalArgumentException("낙찰된 작품만 주문할 수 있습니다");
        }
        if (art.getArtist() == null
                || art.getArtist().getUser() == null
                || !Objects.equals(
                        art.getArtist().getUser().getUserId(),
                        seller.getUserId()
                )) {
            throw new IllegalArgumentException(
                    "작품 판매자와 주문 판매자가 일치하지 않습니다"
            );
        }
        if (winningBid.getArt() == null
                || winningBid.getUser() == null
                || !Objects.equals(
                        winningBid.getArt().getArtId(),
                        art.getArtId()
                )
                || !Objects.equals(
                        winningBid.getUser().getUserId(),
                        buyer.getUserId()
                )) {
            throw new IllegalArgumentException(
                    "낙찰 입찰과 주문 구매자 또는 작품이 일치하지 않습니다"
            );
        }
        if (art.getWinningBid() == null
                || !Objects.equals(
                        art.getWinningBid().getBidId(),
                        winningBid.getBidId()
                )) {
            throw new IllegalArgumentException(
                    "작품에 확정된 낙찰 입찰과 일치하지 않습니다"
            );
        }
        if (winningBid.getBidPrice() == null || winningBid.getBidPrice() < 1) {
            throw new IllegalArgumentException("낙찰가는 1원 이상이어야 합니다");
        }
        if (!paymentDueAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "결제 기한은 주문 생성 시각보다 늦어야 합니다"
            );
        }
        validateSnapshotSource(buyer, "구매자");
        validateSnapshotSource(seller, "판매자");
        requireText(art.getName(), "작품명");
        requireText(art.getImgPath(), "작품 이미지");
    }

    private static void validateSnapshotSource(User user, String role) {
        requireText(user.getUserId(), role + " ID");
        requireText(user.getName(), role + " 이름");
        requireText(user.getNickname(), role + " 닉네임");
        requireText(user.getPhoneNumber(), role + " 연락처");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다");
        }
        return value;
    }

    private static String requireReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(action + " 사유는 필수입니다");
        }
        String normalized = reason.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException(
                    action + " 사유는 200자 이하여야 합니다"
            );
        }
        return normalized;
    }
}
