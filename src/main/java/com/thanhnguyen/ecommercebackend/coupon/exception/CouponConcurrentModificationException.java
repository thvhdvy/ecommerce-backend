package com.thanhnguyen.ecommercebackend.coupon.exception;

public class CouponConcurrentModificationException extends RuntimeException {
    public CouponConcurrentModificationException(Long id) {
        super("Coupon was just modified by another request, please retry: " + id);
    }
}
