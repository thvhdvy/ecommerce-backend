package com.thanhnguyen.ecommercebackend.payment.exception;

public class RefundNotFoundException extends RuntimeException {
    public RefundNotFoundException(Long refundId) {
        super("Refund not found: " + refundId);
    }
}
