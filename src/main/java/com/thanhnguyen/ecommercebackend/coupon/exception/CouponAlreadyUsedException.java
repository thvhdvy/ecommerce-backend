package com.thanhnguyen.ecommercebackend.coupon.exception;

public class CouponAlreadyUsedException extends RuntimeException {
    public CouponAlreadyUsedException(String code) {
        super("You have already used this coupon: " + code);
    }
}
