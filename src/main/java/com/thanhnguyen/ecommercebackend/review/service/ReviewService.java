package com.thanhnguyen.ecommercebackend.review.service;

import com.thanhnguyen.ecommercebackend.review.dto.CreateReviewRequest;
import com.thanhnguyen.ecommercebackend.review.dto.ReviewResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;

public interface ReviewService {
    /** Chỉ cho phép khi customer có order chứa productId đã DELIVERED/COMPLETED; 1 user không review trùng 1 sản phẩm. */
    ReviewResponse create(User currentUser, Long productId, CreateReviewRequest request);

    /**
     * Trả VISIBLE review của tất cả mọi người, cộng thêm HIDDEN review của chính currentUserId
     * (nếu có, để tác giả biết review của mình đã bị ẩn) — currentUserId null với caller ẩn danh.
     */
    List<ReviewResponse> listByProduct(Long productId, Long currentUserId);

    /** Admin ẩn review vi phạm (soft delete qua status), tính lại rating_avg của product. */
    ReviewResponse hide(Long reviewId);

    /** Admin khôi phục review đã ẩn (đảo ngược hide()), tính lại rating_avg của product. */
    ReviewResponse unhide(Long reviewId);
}
