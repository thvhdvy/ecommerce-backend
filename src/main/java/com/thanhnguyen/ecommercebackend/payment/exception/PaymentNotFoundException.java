package com.thanhnguyen.ecommercebackend.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(Long orderId) {
        super("Payment not found for order: " + orderId);
    }
}
