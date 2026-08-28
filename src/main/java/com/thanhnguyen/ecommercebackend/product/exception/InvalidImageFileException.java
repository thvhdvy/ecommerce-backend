package com.thanhnguyen.ecommercebackend.product.exception;

public class InvalidImageFileException extends RuntimeException {
    public InvalidImageFileException(String filename) {
        super("Unsupported image file"
                + (filename != null && !filename.isBlank() ? ": " + filename : "")
                + ". Allowed extensions: jpg, jpeg, png, webp");
    }
}
