package com.thanhnguyen.ecommercebackend.product.exception;

public class MultiplePrimaryImagesException extends RuntimeException {
    public MultiplePrimaryImagesException() {
        super("A product can have at most one primary image");
    }
}
