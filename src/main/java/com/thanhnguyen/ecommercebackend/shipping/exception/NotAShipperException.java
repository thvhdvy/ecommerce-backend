package com.thanhnguyen.ecommercebackend.shipping.exception;

public class NotAShipperException extends RuntimeException {
    public NotAShipperException() {
        super("Target user is not a shipper");
    }
}
