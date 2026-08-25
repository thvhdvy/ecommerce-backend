package com.thanhnguyen.ecommercebackend.shipping.exception;

public class DeliveryOwnershipException extends RuntimeException {
    public DeliveryOwnershipException() {
        super("This delivery is not assigned to you");
    }
}
