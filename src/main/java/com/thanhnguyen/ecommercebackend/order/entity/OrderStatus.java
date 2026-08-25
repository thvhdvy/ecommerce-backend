package com.thanhnguyen.ecommercebackend.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    FAILED_DELIVERY
}
