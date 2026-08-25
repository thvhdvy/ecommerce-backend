package com.thanhnguyen.ecommercebackend.review.repository;

import com.thanhnguyen.ecommercebackend.review.entity.Review;
import com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByProductIdAndUserId(Long productId, Long userId);

    List<Review> findAllByProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.product.id = :productId AND r.status = :status")
    Double findAvgRatingByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);
}
