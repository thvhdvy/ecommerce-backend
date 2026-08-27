package com.thanhnguyen.ecommercebackend.coupon.dto;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponDiscountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Chi la preview (khong reserve luot dung) — xem design doc muc 6.5.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationResponse {
    private String code;
    private CouponDiscountType discountType;
    private BigDecimal discountAmount;
    private BigDecimal finalTotal;
}
