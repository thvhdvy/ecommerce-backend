package com.thanhnguyen.ecommercebackend.product.exception;

public class NotASellerException extends RuntimeException {
    public NotASellerException() {
        super("Only registered sellers can manage products");
    }
}
