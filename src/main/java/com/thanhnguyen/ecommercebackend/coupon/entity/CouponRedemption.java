package com.thanhnguyen.ecommercebackend.coupon.entity;

import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_redemptions")
@Data
@NoArgsConstructor
public class CouponRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    // Reference (khong FK) — Order la nhom giao dich cot loi, cung quy uoc voi
    // payments.order_id/refunds.order_id/deliveries.order_id (design doc muc 0.8).
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "discount_amount_snapshot", nullable = false)
    private BigDecimal discountAmountSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponRedemptionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public CouponRedemption(Coupon coupon, Long orderId, User user, BigDecimal discountAmountSnapshot) {
        this.coupon = coupon;
        this.orderId = orderId;
        this.user = user;
        this.discountAmountSnapshot = discountAmountSnapshot;
        this.status = CouponRedemptionStatus.RESERVED;
    }

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
