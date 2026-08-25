package com.thanhnguyen.ecommercebackend.order.exception;

public class OrderCancelNotAllowedException extends RuntimeException {
    public OrderCancelNotAllowedException() {
        super("Order cannot be cancelled in its current status");
    }
}
