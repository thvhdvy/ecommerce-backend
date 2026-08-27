package com.thanhnguyen.ecommercebackend.returns.exception;

public class ReturnOwnershipException extends RuntimeException {
    public ReturnOwnershipException() {
        super("You do not own this return request");
    }
}
