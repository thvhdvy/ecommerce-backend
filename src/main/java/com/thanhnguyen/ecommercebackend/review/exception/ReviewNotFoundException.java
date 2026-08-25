package com.thanhnguyen.ecommercebackend.review.exception;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long reviewId) {
        super("Review not found with id: " + reviewId);
    }
}
