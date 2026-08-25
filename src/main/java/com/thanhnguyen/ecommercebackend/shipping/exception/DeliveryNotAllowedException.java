package com.thanhnguyen.ecommercebackend.shipping.exception;

public class DeliveryNotAllowedException extends RuntimeException {
    public DeliveryNotAllowedException(String message) {
        super(message);
    }
}
