package com.thanhnguyen.ecommercebackend.user.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account is locked");
    }
}
