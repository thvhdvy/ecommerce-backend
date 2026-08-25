package com.thanhnguyen.ecommercebackend.order.exception;

public class OrderItemStatusNotAllowedException extends RuntimeException {
    public OrderItemStatusNotAllowedException(String message) {
        super(message);
    }
}
