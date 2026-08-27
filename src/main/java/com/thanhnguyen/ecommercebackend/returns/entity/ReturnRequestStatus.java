package com.thanhnguyen.ecommercebackend.returns.entity;

// State machine (design doc muc 7.3):
// REQUESTED -> REJECTED / CANCELLED / APPROVED
// APPROVED -> EXPIRED / ITEM_RECEIVED
// ITEM_RECEIVED -> REFUND_PENDING -> REFUNDED / REFUND_FAILED
// REJECTED, CANCELLED, EXPIRED, REFUNDED la terminal — moi trang thai con lai (ke ca REFUND_FAILED,
// van cho admin retry) deu bi partial unique index chan tao request moi cho cung 1 order_item.
public enum ReturnRequestStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    ITEM_RECEIVED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED
}
