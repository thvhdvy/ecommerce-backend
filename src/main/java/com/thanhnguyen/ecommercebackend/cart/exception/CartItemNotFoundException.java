package com.thanhnguyen.ecommercebackend.cart.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long id) {
        super("Cart item not found: " + id);
    }
}
