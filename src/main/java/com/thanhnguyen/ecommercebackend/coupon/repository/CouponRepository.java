package com.thanhnguyen.ecommercebackend.coupon.repository;

import com.thanhnguyen.ecommercebackend.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);

    // Dieu kien WHERE chinh la hang rao chong race — usage_limit null nghia la khong gioi han.
    // Cung ky thuat voi Inventory.reserveStock (design doc muc 0.9): conditional UPDATE, khong
    // doc-roi-ghi. version duoc bump thu cong vi bulk @Modifying bo qua co che optimistic-lock
    // tu dong cua Hibernate (chi ap dung cho duong entity save() — xem Coupon.version javadoc).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.usageReserved = c.usageReserved + 1, c.version = c.version + 1, "
            + "c.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE c.id = :couponId AND (c.usageLimit IS NULL OR c.usageReserved + c.usageCommitted < c.usageLimit)")
    int reserveUsage(@Param("couponId") Long couponId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.usageReserved = c.usageReserved - 1, c.version = c.version + 1, "
            + "c.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE c.id = :couponId AND c.usageReserved >= 1")
    int releaseUsage(@Param("couponId") Long couponId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.usageReserved = c.usageReserved - 1, c.usageCommitted = c.usageCommitted + 1, "
            + "c.version = c.version + 1, c.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE c.id = :couponId AND c.usageReserved >= 1")
    int commitUsage(@Param("couponId") Long couponId);
}
