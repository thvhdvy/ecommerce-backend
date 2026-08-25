package com.thanhnguyen.ecommercebackend.user.exception;

public class NotEligibleForSellerException extends RuntimeException {
    public NotEligibleForSellerException() {
        super("Only CUSTOMER accounts can register as a seller");
    }
}
