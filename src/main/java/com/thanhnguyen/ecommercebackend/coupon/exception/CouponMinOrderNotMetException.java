package com.thanhnguyen.ecommercebackend.coupon.exception;

import java.math.BigDecimal;

public class CouponMinOrderNotMetException extends RuntimeException {
    public CouponMinOrderNotMetException(BigDecimal minOrderAmount) {
        super("Order does not meet the minimum amount required for this coupon: " + minOrderAmount);
    }
}
