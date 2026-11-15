package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.ArtistReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewArtOptionDto;
import com.dailyatelier.dailyatelier.dto.ReviewCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.ReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewSort;
import com.dailyatelier.dailyatelier.dto.ReviewUpdateRequestDto;
import com.dailyatelier.dailyatelier.dto.ReviewWriteResponseDto;
import com.dailyatelier.dailyatelier.dto.UnreviewedSoldArtDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.Review;
import com.dailyatelier.dailyatelier.exception.ReviewApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<Integer> ENDED_ART_STATUSES = List.of(
            Art.STATUS_SOLD,
            Art.STATUS_UNSOLD
    );

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ArtRepository artRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReviewWriteResponseDto getWriteReview(String userId, Long orderId) {
        Order order = findOrder(orderId);
        validateBuyer(order, userId);
        validateConfirmed(order);
        Review review = reviewRepository.findByOrderOrderId(orderId).orElse(null);
        return ReviewWriteResponseDto.of(order, review);
    }

    @Transactional
    public ReviewResponseDto create(
            String userId,
            ReviewCreateRequestDto request) {
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(this::orderNotFound);
        validateBuyer(order, userId);
        validateConfirmed(order);
        if (reviewRepository.existsByOrderOrderId(order.getOrderId())) {
            throw duplicateReview();
        }

        try {
            Review review = Review.create(
                    order,
                    request.getStar(),
                    request.getContent(),
                    LocalDateTime.now(clock)
            );
            return ReviewResponseDto.from(reviewRepository.saveAndFlush(review));
        } catch (IllegalArgumentException exception) {
            throw invalidReview(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReview();
        }
    }

    @Transactional
    public ReviewResponseDto update(
            String userId,
            Long reviewId,
            ReviewUpdateRequestDto request) {
        Review review = findReview(reviewId);
        if (!review.getUser().getUserId().equals(userId)) {
            throw accessDenied("본인이 작성한 리뷰만 수정할 수 있습니다.");
        }
        try {
            review.update(
                    request.getStar(),
                    request.getContent(),
                    LocalDateTime.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw invalidReview(exception.getMessage());
        }
        return ReviewResponseDto.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewPageResponseDto getMyReviews(
            String userId,
            ReviewSort sort,
            int page,
            int size) {
        Page<Review> reviews = reviewRepository.findByUserUserId(
                userId,
                pageable(sort, page, size)
        );
        return new ReviewPageResponseDto(
                reviews.getContent().stream().map(ReviewResponseDto::from).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ArtistReviewPageResponseDto getArtistReviews(
            String userId,
            Long artId,
            ReviewSort sort,
            int page,
            int size) {
        List<Art> arts = artRepository
                .findByArtistUserUserIdOrderByNameAscArtIdAsc(userId);
        if (artId != null && arts.stream().noneMatch(art -> artId.equals(art.getArtId()))) {
            throw accessDenied("본인의 작품 리뷰만 조회할 수 있습니다.");
        }

        Page<Review> reviews = reviewRepository.findArtistReviews(
                userId,
                artId,
                pageable(sort, page, size)
        );
        long soldArtCount = artRepository.countByArtistUserUserIdAndArtStatus(
                userId,
                Art.STATUS_SOLD
        );
        long reviewedArtCount = reviewRepository.countReviewedSoldArts(
                userId,
                Art.STATUS_SOLD
        );
        return new ArtistReviewPageResponseDto(
                reviews.getContent().stream().map(ReviewResponseDto::from).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                reviewRepository.countArtistReviews(userId),
                artRepository.countByArtistUserUserIdAndArtStatusIn(
                        userId,
                        ENDED_ART_STATUSES
                ),
                reviewRepository.averageArtistStar(userId, artId),
                arts.stream().map(ReviewArtOptionDto::from).toList(),
                soldArtCount,
                reviewedArtCount,
                soldArtCount - reviewedArtCount,
                artRepository.findUnreviewedSoldArts(userId, Art.STATUS_SOLD)
                        .stream()
                        .map(UnreviewedSoldArtDto::from)
                        .toList()
        );
    }

    private PageRequest pageable(ReviewSort sort, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReviewApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_PAGE",
                    "페이지는 0 이상, 페이지 크기는 1 이상 50 이하여야 합니다."
            );
        }
        ReviewSort selected = sort == null ? ReviewSort.RECENT : sort;
        Sort selectedSort = switch (selected) {
            case RECENT -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("reviewId")
            );
            case STAR -> Sort.by(
                    Sort.Order.desc("star"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("reviewId")
            );
            case PRICE -> Sort.by(
                    Sort.Order.desc("order.winningPrice"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("reviewId")
            );
        };
        return PageRequest.of(page, size, selectedSort);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(this::orderNotFound);
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewApiException(
                        HttpStatus.NOT_FOUND,
                        "REVIEW_NOT_FOUND",
                        "리뷰를 찾을 수 없습니다."
                ));
    }

    private void validateBuyer(Order order, String userId) {
        if (!order.getBuyer().getUserId().equals(userId)) {
            throw accessDenied("본인의 주문에만 리뷰를 작성할 수 있습니다.");
        }
    }

    private void validateConfirmed(Order order) {
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ReviewApiException(
                    HttpStatus.CONFLICT,
                    "REVIEW_ORDER_NOT_CONFIRMED",
                    "구매 확정된 주문에만 리뷰를 작성할 수 있습니다."
            );
        }
    }

    private ReviewApiException orderNotFound() {
        return new ReviewApiException(
                HttpStatus.NOT_FOUND,
                "REVIEW_ORDER_NOT_FOUND",
                "리뷰 대상 주문을 찾을 수 없습니다."
        );
    }

    private ReviewApiException invalidReview(String message) {
        return new ReviewApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REVIEW",
                message
        );
    }

    private ReviewApiException accessDenied(String message) {
        return new ReviewApiException(
                HttpStatus.FORBIDDEN,
                "REVIEW_ACCESS_DENIED",
                message
        );
    }

    private ReviewApiException duplicateReview() {
        return new ReviewApiException(
                HttpStatus.CONFLICT,
                "REVIEW_ALREADY_EXISTS",
                "이미 리뷰가 작성된 주문입니다."
        );
    }
}
