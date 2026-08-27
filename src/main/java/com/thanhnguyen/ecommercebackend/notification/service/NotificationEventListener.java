package com.thanhnguyen.ecommercebackend.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.event.OrderNotificationEvent;
import com.thanhnguyen.ecommercebackend.notification.repository.NotificationRepository;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Lang nghe domain event tu Order/Payment module qua Spring ApplicationEventPublisher (KHONG phai
 * message broker — cho phep boi design doc muc 0.8) va ghi 1 dong outbox vao bang notifications.
 * BEFORE_COMMIT: chay trong CUNG transaction voi thay doi trang thai order — dam bao khong bao gio
 * "doi status thanh cong nhung quen ghi outbox" (design doc v2 muc 8.3). Day chi la INSERT thuan DB,
 * khong phai network call, nen an toan nam chung transaction (khac voi buoc gui email that o
 * NotificationDispatcher, luon chay ngoai transaction).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final OrderService orderService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderNotification(OrderNotificationEvent event) {
        Long customerId = orderService.getCustomerIdByOrderId(event.orderId());
        if (customerId == null) {
            log.warn("Order {} not found when handling notification event {} — skipping", event.orderId(), event.type());
            return;
        }
        User user = userService.getEntityById(customerId);

        String payload = buildPayload(event, user);
        notificationRepository.save(new Notification(user, event.type(), event.orderId(), payload));
    }

    private String buildPayload(OrderNotificationEvent event, User user) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId", event.orderId(),
                    "customerName", user.getFullName()));
        } catch (JsonProcessingException ex) {
            // Khong nen xay ra voi du lieu don gian the nay — coi la loi lap trinh neu co.
            throw new IllegalStateException("Failed to build notification payload for order " + event.orderId(), ex);
        }
    }
}
