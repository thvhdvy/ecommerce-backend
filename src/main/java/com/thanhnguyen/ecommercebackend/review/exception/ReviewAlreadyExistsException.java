package com.thanhnguyen.ecommercebackend.review.exception;

public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException() {
        super("You have already reviewed this product");
    }
}
