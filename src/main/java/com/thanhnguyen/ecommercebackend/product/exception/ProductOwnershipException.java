package com.thanhnguyen.ecommercebackend.product.exception;

public class ProductOwnershipException extends RuntimeException {
    public ProductOwnershipException() {
        super("You do not own this product");
    }
}
