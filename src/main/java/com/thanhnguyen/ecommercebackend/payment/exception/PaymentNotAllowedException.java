package com.thanhnguyen.ecommercebackend.payment.exception;

public class PaymentNotAllowedException extends RuntimeException {
    public PaymentNotAllowedException(String message) {
        super(message);
    }
}
