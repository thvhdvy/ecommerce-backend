package com.thanhnguyen.ecommercebackend.returns.exception;

public class ReturnNotFoundException extends RuntimeException {
    public ReturnNotFoundException(Long id) {
        super("Return request not found: " + id);
    }
}
