package com.thanhnguyen.ecommercebackend.review.repository;

import com.thanhnguyen.ecommercebackend.review.entity.Review;
import com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.product.id = :productId AND r.status = :status")
    Double findAvgRatingByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    /**
     * VISIBLE review cua tat ca moi nguoi, cong them HIDDEN review cua chinh currentUserId (neu co) —
     * currentUserId co the null (anonymous), khi do phan HIDDEN khong bao gio match (so sanh voi NULL).
     * JOIN FETCH r.user (to-one) an toan voi pagination; countQuery rieng vi Spring Data khong tu
     * suy ra duoc count tu query co JOIN FETCH. Sort lay tu Pageable (controller default createdAt DESC).
     */
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.user WHERE r.product.id = :productId "
            + "AND (r.status = com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus.VISIBLE "
            + "OR (r.status = com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus.HIDDEN AND r.user.id = :currentUserId))",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId "
            + "AND (r.status = com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus.VISIBLE "
            + "OR (r.status = com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus.HIDDEN AND r.user.id = :currentUserId))")
    Page<Review> findAllVisibleOrOwnByProductId(
            @Param("productId") Long productId, @Param("currentUserId") Long currentUserId, Pageable pageable);
}
