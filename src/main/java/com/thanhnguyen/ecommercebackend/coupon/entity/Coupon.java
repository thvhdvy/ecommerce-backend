package com.thanhnguyen.ecommercebackend.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    // Chi co y nghia voi PERCENTAGE (chan tran so tien giam) — null = khong cap.
    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false)
    private BigDecimal minOrderAmount;

    // null = khong gioi han luot dung toan he thong.
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_reserved", nullable = false)
    private Integer usageReserved;

    @Column(name = "usage_committed", nullable = false)
    private Integer usageCommitted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    // Bao ve duong admin sua tay (VD ha usage_limit) — khong bao ve hot path reserve/release/commit,
    // hot path dung dieu kien WHERE trong conditional UPDATE (xem CouponRepository), cung nguyen tac
    // 2-co-che-song-song da ap dung cho Inventory (design doc muc 0.9).
    @Version
    private Long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
