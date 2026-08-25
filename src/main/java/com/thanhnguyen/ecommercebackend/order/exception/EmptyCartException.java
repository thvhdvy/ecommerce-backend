package com.thanhnguyen.ecommercebackend.order.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException() {
        super("Cannot checkout an empty cart");
    }
}
