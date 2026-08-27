package com.thanhnguyen.ecommercebackend.payout.exception;

public class PayoutNotAllowedException extends RuntimeException {
    public PayoutNotAllowedException(String message) {
        super(message);
    }
}
