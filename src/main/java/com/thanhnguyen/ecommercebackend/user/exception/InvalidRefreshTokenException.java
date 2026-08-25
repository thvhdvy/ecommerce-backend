package com.thanhnguyen.ecommercebackend.user.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired, or revoked");
    }
}
