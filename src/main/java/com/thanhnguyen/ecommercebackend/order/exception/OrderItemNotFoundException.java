package com.thanhnguyen.ecommercebackend.order.exception;

public class OrderItemNotFoundException extends RuntimeException {
    public OrderItemNotFoundException(Long id) {
        super("Order item not found: " + id);
    }
}
