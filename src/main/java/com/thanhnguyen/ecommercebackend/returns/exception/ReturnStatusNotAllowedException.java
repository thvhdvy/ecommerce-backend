package com.thanhnguyen.ecommercebackend.returns.exception;

/** Thao tac khong hop le voi trang thai hien tai cua return request (VD approve khi da REJECTED). */
public class ReturnStatusNotAllowedException extends RuntimeException {
    public ReturnStatusNotAllowedException(String message) {
        super(message);
    }
}
