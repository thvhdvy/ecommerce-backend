package com.thanhnguyen.ecommercebackend.coupon.exception;

// Bao trum: chua ACTIVE, chua toi starts_at, hoac qua ends_at (design doc muc 6.2).
public class CouponInvalidException extends RuntimeException {
    public CouponInvalidException(String message) {
        super(message);
    }
}
