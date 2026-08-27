package com.thanhnguyen.ecommercebackend.returns.exception;

/** order_item da co 1 return request khac chua terminal — chan boi unique index (design doc muc 7.6). */
public class ReturnAlreadyActiveException extends RuntimeException {
    public ReturnAlreadyActiveException(Long orderItemId) {
        super("Order item " + orderItemId + " already has an active return request");
    }
}
