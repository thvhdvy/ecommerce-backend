package com.thanhnguyen.ecommercebackend.user.exception;

// Chuyen tu product.exception sang user.exception: exception nay thuoc ve seller identity
// (SellerService.requireActiveSeller), khong phai nghiep vu product — de user module throw duoc
// ma khong phu thuoc nguoc vao product module.
public class NotASellerException extends RuntimeException {
    public NotASellerException() {
        super("Only registered sellers can manage products");
    }
}
