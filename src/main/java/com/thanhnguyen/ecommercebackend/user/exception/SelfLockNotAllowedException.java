package com.thanhnguyen.ecommercebackend.user.exception;

public class SelfLockNotAllowedException extends RuntimeException {
    public SelfLockNotAllowedException() {
        super("You cannot lock your own account");
    }
}
