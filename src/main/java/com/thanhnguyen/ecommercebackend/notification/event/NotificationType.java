package com.thanhnguyen.ecommercebackend.notification.event;

/**
 * Song trong package `event` (khong phai `entity`) vi day la kieu du lieu duoc Order/Payment module
 * tham chieu khi publish OrderNotificationEvent — giu nguyen tac "khong import thang entity module
 * khac" (design doc muc 0.8) trong khi van dung chung 1 enum cho ca event lan cot notifications.type.
 */
public enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    ORDER_REFUNDED
}
