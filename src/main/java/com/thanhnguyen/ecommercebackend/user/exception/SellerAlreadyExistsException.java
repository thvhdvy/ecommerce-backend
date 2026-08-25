package com.thanhnguyen.ecommercebackend.user.exception;

public class SellerAlreadyExistsException extends RuntimeException {
    public SellerAlreadyExistsException() {
        super("This user already has a seller profile");
    }
}
