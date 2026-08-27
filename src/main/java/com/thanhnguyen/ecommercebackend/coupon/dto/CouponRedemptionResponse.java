package com.thanhnguyen.ecommercebackend.coupon.dto;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponRedemptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemptionResponse {
    private Long id;
    private Long couponId;
    private Long orderId;
    private Long userId;
    private BigDecimal discountAmountSnapshot;
    private CouponRedemptionStatus status;
    private LocalDateTime createdAt;
}
