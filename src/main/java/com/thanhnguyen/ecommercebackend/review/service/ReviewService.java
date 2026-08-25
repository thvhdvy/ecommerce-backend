package com.thanhnguyen.ecommercebackend.review.service;

import com.thanhnguyen.ecommercebackend.review.dto.CreateReviewRequest;
import com.thanhnguyen.ecommercebackend.review.dto.ReviewResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;

public interface ReviewService {
    /** Chỉ cho phép khi customer có order chứa productId đã DELIVERED/COMPLETED; 1 user không review trùng 1 sản phẩm. */
    ReviewResponse create(User currentUser, Long productId, CreateReviewRequest request);

    /** Public: chỉ trả review VISIBLE. */
    List<ReviewResponse> listVisibleByProduct(Long productId);

    /** Admin ẩn review vi phạm (soft delete qua status), tính lại rating_avg của product. */
    ReviewResponse hide(Long reviewId);
}
