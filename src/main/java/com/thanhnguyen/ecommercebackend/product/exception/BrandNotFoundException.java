package com.thanhnguyen.ecommercebackend.product.exception;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException(Long id) {
        super("Brand not found: " + id);
    }
}
