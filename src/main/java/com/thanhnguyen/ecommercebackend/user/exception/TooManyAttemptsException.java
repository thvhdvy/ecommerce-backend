package com.thanhnguyen.ecommercebackend.user.exception;

public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException() {
        super("Too many attempts, please try again later");
    }
}
