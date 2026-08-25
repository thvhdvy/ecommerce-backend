package com.thanhnguyen.ecommercebackend.product.exception;

public class InvalidSortFieldException extends RuntimeException {
    public InvalidSortFieldException(String field) {
        super("Cannot sort by field: " + field);
    }
}
