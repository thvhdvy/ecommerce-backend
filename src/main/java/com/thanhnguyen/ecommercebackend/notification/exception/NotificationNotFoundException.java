package com.thanhnguyen.ecommercebackend.notification.exception;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
