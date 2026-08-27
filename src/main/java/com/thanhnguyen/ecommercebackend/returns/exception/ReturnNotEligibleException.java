package com.thanhnguyen.ecommercebackend.returns.exception;

/** Order/order_item khong du dieu kien tao return (chua DELIVERED, qua RETURN_WINDOW_DAYS...). */
public class ReturnNotEligibleException extends RuntimeException {
    public ReturnNotEligibleException(String message) {
        super(message);
    }
}
