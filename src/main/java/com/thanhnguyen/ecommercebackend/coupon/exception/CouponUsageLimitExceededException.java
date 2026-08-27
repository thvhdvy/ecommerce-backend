package com.thanhnguyen.ecommercebackend.coupon.exception;

public class CouponUsageLimitExceededException extends RuntimeException {
    public CouponUsageLimitExceededException(String code) {
        super("Coupon has reached its usage limit: " + code);
    }
}
