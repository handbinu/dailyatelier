package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtistReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewSort;
import com.dailyatelier.dailyatelier.dto.ReviewUpdateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.Review;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.ReviewApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:review-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ReviewService.class, ReviewServiceTest.FixedClockConfig.class})
class ReviewServiceTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 26, 12, 0);

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ArtRepository artRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private UserRepository userRepository;

    private User buyer;
    private User otherBuyer;
    private User seller;
    private User otherSeller;
    private Artist artist;
    private Artist otherArtist;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(user("buyer", 0));
        otherBuyer = userRepository.save(user("buyer2", 0));
        seller = userRepository.save(user("seller", 1));
        otherSeller = userRepository.save(user("seller2", 1));
        artist = artistRepository.save(artist(seller, "판매 작가"));
        otherArtist = artistRepository.save(artist(otherSeller, "다른 작가"));
    }

    @Test
    void createsOnlyFromConfirmedOwnedOrderAndRejectsInvalidEligibility() {
        Order confirmed = saveOrder(
                "확정 작품", buyer, seller, artist, 150_000, true);
        Order pending = saveOrder(
                "대기 작품", buyer, seller, artist, 170_000, false);

        ReviewResponseDto created = reviewService.create(
                buyer.getUserId(),
                new ReviewCreateRequestDto(
                        confirmed.getOrderId(),
                        9,
                        "  주문 관계로 저장되는 충분히 긴 리뷰  "
                )
        );

        assertThat(created.orderId()).isEqualTo(confirmed.getOrderId());
        assertThat(created.artId()).isEqualTo(confirmed.getArt().getArtId());
        assertThat(created.buyerNickname()).isEqualTo(buyer.getNickname());
        assertThat(created.content()).isEqualTo("주문 관계로 저장되는 충분히 긴 리뷰");
        assertThat(reviewRepository.findById(created.reviewId()).orElseThrow())
                .satisfies(review -> {
                    assertThat(review.getUser()).isEqualTo(buyer);
                    assertThat(review.getArt()).isEqualTo(confirmed.getArt());
                    assertThat(review.getOrder()).isEqualTo(confirmed);
                });

        assertCode(
                () -> reviewService.create(
                        otherBuyer.getUserId(),
                        new ReviewCreateRequestDto(
                                confirmed.getOrderId(), 8, "타인의 주문에 작성하는 긴 리뷰"
                        )
                ),
                "REVIEW_ACCESS_DENIED"
        );
        assertCode(
                () -> reviewService.create(
                        buyer.getUserId(),
                        new ReviewCreateRequestDto(
                                pending.getOrderId(), 8, "확정 전 주문에 작성하는 긴 리뷰"
                        )
                ),
                "REVIEW_ORDER_NOT_CONFIRMED"
        );
        assertCode(
                () -> reviewService.create(
                        buyer.getUserId(),
                        new ReviewCreateRequestDto(
                                confirmed.getOrderId(), 8, "중복으로 작성하려는 충분히 긴 리뷰"
                        )
                ),
                "REVIEW_ALREADY_EXISTS"
        );
    }

    @Test
    void writeLookupAndUpdateEnforceOwnership() {
        Order confirmed = saveOrder(
                "수정 작품", buyer, seller, artist, 210_000, true);
        ReviewResponseDto created = reviewService.create(
                buyer.getUserId(),
                new ReviewCreateRequestDto(
                        confirmed.getOrderId(), 6, "처음 작성하는 충분히 긴 리뷰 내용"
                )
        );

        assertThat(reviewService.getWriteReview(
                buyer.getUserId(), confirmed.getOrderId()).review().reviewId())
                .isEqualTo(created.reviewId());
        assertCode(
                () -> reviewService.getWriteReview(
                        otherBuyer.getUserId(), confirmed.getOrderId()),
                "REVIEW_ACCESS_DENIED"
        );
        assertCode(
                () -> reviewService.update(
                        otherBuyer.getUserId(),
                        created.reviewId(),
                        new ReviewUpdateRequestDto(10, "타인이 수정하려는 충분히 긴 리뷰")
                ),
                "REVIEW_ACCESS_DENIED"
        );

        ReviewResponseDto updated = reviewService.update(
                buyer.getUserId(),
                created.reviewId(),
                new ReviewUpdateRequestDto(10, "  본인이 수정한 충분히 긴 리뷰 내용  ")
        );
        assertThat(updated.star()).isEqualTo(10);
        assertThat(updated.content()).isEqualTo("본인이 수정한 충분히 긴 리뷰 내용");
        assertThat(updated.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void buyerAndArtistQueriesApplyScopeSortFilterAndStatistics() {
        Order highStar = saveOrder(
                "높은 별점", buyer, seller, artist, 100_000, true);
        Order highPrice = saveOrder(
                "높은 낙찰가", buyer, seller, artist, 300_000, true);
        Order otherArtistOrder = saveOrder(
                "다른 작가 작품", buyer, otherSeller, otherArtist, 500_000, true);
        reviewService.create(
                buyer.getUserId(),
                new ReviewCreateRequestDto(
                        highStar.getOrderId(), 9, "별점 정렬을 확인하는 충분히 긴 리뷰"
                )
        );
        reviewService.create(
                buyer.getUserId(),
                new ReviewCreateRequestDto(
                        highPrice.getOrderId(), 5, "가격 정렬을 확인하는 충분히 긴 리뷰"
                )
        );
        reviewService.create(
                buyer.getUserId(),
                new ReviewCreateRequestDto(
                        otherArtistOrder.getOrderId(), 10, "다른 작가 범위를 확인하는 긴 리뷰"
                )
        );
        saveArt("유찰 작품", artist, Art.STATUS_UNSOLD);
        Art unreviewedSold = saveArt("리뷰 미작성 판매 작품", artist, Art.STATUS_SOLD);
        saveArt("진행 작품", artist, Art.STATUS_ACTIVE);
        saveArt("취소 작품", artist, Art.STATUS_CANCELED);

        ReviewPageResponseDto myReviews = reviewService.getMyReviews(
                buyer.getUserId(), ReviewSort.RECENT, 0, 2);
        ArtistReviewPageResponseDto starSorted = reviewService.getArtistReviews(
                seller.getUserId(), null, ReviewSort.STAR, 0, 6);
        ArtistReviewPageResponseDto priceSorted = reviewService.getArtistReviews(
                seller.getUserId(), null, ReviewSort.PRICE, 0, 6);
        ArtistReviewPageResponseDto filtered = reviewService.getArtistReviews(
                seller.getUserId(), highStar.getArt().getArtId(),
                ReviewSort.RECENT, 0, 6);

        assertThat(myReviews.totalElements()).isEqualTo(3);
        assertThat(myReviews.content()).hasSize(2);
        assertThat(starSorted.totalReviewCount()).isEqualTo(2);
        assertThat(starSorted.endedArtCount()).isEqualTo(4);
        assertThat(starSorted.soldArtCount()).isEqualTo(3);
        assertThat(starSorted.reviewedArtCount()).isEqualTo(2);
        assertThat(starSorted.unreviewedArtCount()).isEqualTo(1);
        assertThat(starSorted.unreviewedSoldArts())
                .singleElement()
                .satisfies(art -> {
                    assertThat(art.artId()).isEqualTo(unreviewedSold.getArtId());
                    assertThat(art.artName()).isEqualTo("리뷰 미작성 판매 작품");
                });
        assertThat(starSorted.averageStar()).isEqualTo(7.0);
        assertThat(starSorted.content())
                .extracting(ReviewResponseDto::star)
                .containsExactly(9, 5);
        assertThat(priceSorted.content())
                .extracting(ReviewResponseDto::winningPrice)
                .containsExactly(300_000, 100_000);
        assertThat(filtered.totalReviewCount()).isEqualTo(2);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.averageStar()).isEqualTo(9.0);
        assertThat(filtered.arts()).hasSize(6);

        assertCode(
                () -> reviewService.getArtistReviews(
                        seller.getUserId(),
                        otherArtistOrder.getArt().getArtId(),
                        ReviewSort.RECENT, 0, 6),
                "REVIEW_ACCESS_DENIED"
        );
    }

    @Test
    void databaseUniqueConstraintRejectsSecondReviewForSameOrder() {
        Order order = saveOrder(
                "유니크 작품", buyer, seller, artist, 120_000, true);
        reviewRepository.saveAndFlush(Review.create(
                order, 8, "첫 번째로 저장하는 충분히 긴 리뷰", NOW));

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(Review.create(
                order, 9, "두 번째로 저장하는 충분히 긴 리뷰", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order saveOrder(
            String name,
            User orderBuyer,
            User orderSeller,
            Artist orderArtist,
            int price,
            boolean confirmed) {
        Art art = saveArt(name, orderArtist, Art.STATUS_ACTIVE);
        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(orderBuyer);
        bid.setBidPrice(price);
        bid.setBidTime(NOW.minusMinutes(1));
        bid = bidRepository.save(bid);

        art.setWinningBid(bid);
        art.setCurrentPrice(price);
        art.setArtStatus(Art.STATUS_SOLD);
        art.setClosedAt(NOW);
        artRepository.save(art);

        Order order = Order.create(
                art,
                bid,
                orderBuyer,
                orderSeller,
                NOW,
                NOW.plusHours(24),
                OrderShippingAddress.of(
                        orderBuyer.getName(),
                        orderBuyer.getPhoneNumber(),
                        "02535",
                        "서울특별시 중랑구",
                        null
                )
        );
        if (confirmed) {
            order.transitionTo(OrderStatus.PAID, NOW.plusMinutes(1), null);
            order.transitionTo(OrderStatus.PREPARING, NOW.plusMinutes(2), null);
            order.transitionTo(OrderStatus.SHIPPED, NOW.plusMinutes(3), null);
            order.transitionTo(OrderStatus.DELIVERED, NOW.plusMinutes(4), null);
            order.transitionTo(OrderStatus.CONFIRMED, NOW.plusMinutes(5), null);
        }
        return orderRepository.save(order);
    }

    private Art saveArt(String name, Artist owner, int status) {
        Art art = new Art();
        art.setArtist(owner);
        art.setName(name);
        art.setFormat(ArtFormat.PHYSICAL);
        art.setCategory(ArtCategory.OTHER);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setMinimumBidIncrement(1_000);
        art.setBidStartTime(NOW.minusDays(2));
        art.setClosingTime(NOW.minusDays(1));
        art.setImgPath("https://example.com/" + name + ".jpg");
        art.setArtStatus(status);
        art.setClosedAt(status == Art.STATUS_ACTIVE ? null : NOW.minusDays(1));
        return artRepository.save(art);
    }

    private User user(String userId, int status) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(userId);
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(NOW.minusDays(30));
        user.setUserStatus(status);
        return user;
    }

    private Artist artist(User user, String name) {
        Artist artist = new Artist();
        artist.setUser(user);
        artist.setArtistName(name);
        return artist;
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ReviewApiException.class)
                .satisfies(error -> assertThat(((ReviewApiException) error).getCode())
                        .isEqualTo(code));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-08-26T03:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
