package com.thanhnguyen.ecommercebackend.review.service;

import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.product.service.ProductService;
import com.thanhnguyen.ecommercebackend.review.dto.CreateReviewRequest;
import com.thanhnguyen.ecommercebackend.review.dto.ReviewResponse;
import com.thanhnguyen.ecommercebackend.review.entity.Review;
import com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewNotEligibleException;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewNotFoundException;
import com.thanhnguyen.ecommercebackend.review.repository.ReviewRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderService orderService;
    private final ProductService productService;

    @Override
    @Transactional
    public ReviewResponse create(User currentUser, Long productId, CreateReviewRequest request) {
        Long eligibleOrderId = orderService.findEligibleOrderIdForReview(currentUser, productId);
        if (eligibleOrderId == null) {
            throw new ReviewNotEligibleException();
        }

        if (reviewRepository.existsByProductIdAndUserId(productId, currentUser.getId())) {
            throw new ReviewAlreadyExistsException();
        }

        Product product = productService.getEntityById(productId);

        Review review = new Review(product, currentUser, eligibleOrderId, request.getRating(), request.getComment());
        Review saved;
        try {
            // saveAndFlush (khong phai save thuong) de bat DataIntegrityViolationException ngay tai day,
            // phong TOCTOU: 2 request dong thoi cung qua duoc check existsBy... o tren truoc khi commit,
            // request thua se vo unique constraint (product_id, user_id) o DB, khong de rot xuong 500 chung.
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw new ReviewAlreadyExistsException();
        }

        recalculateProductRating(productId);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listByProduct(Long productId, Long currentUserId, Pageable pageable) {
        return PageResponse.from(
                reviewRepository.findAllVisibleOrOwnByProductId(productId, currentUserId, pageable)
                        .map(this::toResponse));
    }

    @Override
    @Transactional
    public ReviewResponse hide(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        review.setStatus(ReviewStatus.HIDDEN);
        Review saved = reviewRepository.save(review);

        recalculateProductRating(saved.getProduct().getId());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReviewResponse unhide(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        review.setStatus(ReviewStatus.VISIBLE);
        Review saved = reviewRepository.save(review);

        recalculateProductRating(saved.getProduct().getId());

        return toResponse(saved);
    }

    private void recalculateProductRating(Long productId) {
        // Product module khoa row product truoc, roi moi goi supplier nay doc AVG (xem javadoc
        // ProductService.recalculateRating) — thu tu lock-truoc-doc-sau giu nguyen nhu truoc,
        // nhung viec khoa row thuoc ve Product module, Review khong dung ProductRepository nua.
        productService.recalculateRating(productId, () -> {
            Double avg = reviewRepository.findAvgRatingByProductIdAndStatus(productId, ReviewStatus.VISIBLE);
            return BigDecimal.valueOf(avg == null ? 0.0 : avg).setScale(2, RoundingMode.HALF_UP);
        });
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                review.getUser().getFullName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}
