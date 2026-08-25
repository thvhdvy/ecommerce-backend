package com.thanhnguyen.ecommercebackend.order.exception;

public class OrderOwnershipException extends RuntimeException {
    public OrderOwnershipException() {
        super("You do not own this order");
    }
}
