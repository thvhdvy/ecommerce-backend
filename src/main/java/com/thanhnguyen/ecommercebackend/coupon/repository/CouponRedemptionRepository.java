package com.thanhnguyen.ecommercebackend.coupon.repository;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponRedemption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    // 1 order chi co toi da 1 redemption (design doc muc 6.1: 1 order = toi da 1 coupon).
    Optional<CouponRedemption> findByOrderId(Long orderId);

    Page<CouponRedemption> findAllByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable);
}
