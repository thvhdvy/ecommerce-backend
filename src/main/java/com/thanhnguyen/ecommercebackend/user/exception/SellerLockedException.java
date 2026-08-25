package com.thanhnguyen.ecommercebackend.user.exception;

public class SellerLockedException extends RuntimeException {
    public SellerLockedException() {
        super("Seller account is locked");
    }
}
