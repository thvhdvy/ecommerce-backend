package com.thanhnguyen.ecommercebackend.user.exception;

public class SellerNotFoundException extends RuntimeException {
    public SellerNotFoundException(Long sellerId) {
        super("Seller not found with id: " + sellerId);
    }
}
