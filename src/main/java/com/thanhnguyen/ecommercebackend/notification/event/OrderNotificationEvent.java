package com.thanhnguyen.ecommercebackend.notification.event;

/**
 * Domain event noi bo (Spring ApplicationEventPublisher, KHONG phai message broker — design doc
 * muc 0.8/8.3) publish boi Order/Payment module khi order doi trang thai khach hang quan tam.
 * Chi mang orderId — NotificationEventListener tu resolve customerId qua OrderService khi xu ly,
 * tranh truyen du lieu thua va giu module Order/Payment khong biet Notification ton tai.
 */
public record OrderNotificationEvent(NotificationType type, Long orderId) {
}
