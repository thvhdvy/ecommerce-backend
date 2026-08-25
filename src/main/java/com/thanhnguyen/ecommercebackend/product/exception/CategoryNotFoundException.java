package com.thanhnguyen.ecommercebackend.product.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category not found: " + id);
    }
}
