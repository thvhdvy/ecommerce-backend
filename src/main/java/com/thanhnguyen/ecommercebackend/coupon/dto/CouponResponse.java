package com.thanhnguyen.ecommercebackend.coupon.dto;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponDiscountType;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponResponse {
    private Long id;
    private String code;
    private CouponDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private Integer usageLimit;
    private Integer usageReserved;
    private Integer usageCommitted;
    private CouponStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
