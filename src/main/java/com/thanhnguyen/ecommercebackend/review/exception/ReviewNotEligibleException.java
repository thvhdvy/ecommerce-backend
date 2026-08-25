package com.thanhnguyen.ecommercebackend.review.exception;

public class ReviewNotEligibleException extends RuntimeException {
    public ReviewNotEligibleException() {
        super("You can only review a product after it has been delivered to you");
    }
}
