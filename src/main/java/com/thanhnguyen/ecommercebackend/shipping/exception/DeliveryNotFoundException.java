package com.thanhnguyen.ecommercebackend.shipping.exception;

public class DeliveryNotFoundException extends RuntimeException {
    public DeliveryNotFoundException(Long id) {
        super("Delivery not found: " + id);
    }
}
