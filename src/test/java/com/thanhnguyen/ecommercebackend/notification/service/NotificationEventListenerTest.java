package com.thanhnguyen.ecommercebackend.notification.service;

import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import com.thanhnguyen.ecommercebackend.notification.event.OrderNotificationEvent;
import com.thanhnguyen.ecommercebackend.notification.repository.NotificationRepository;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private UserService userService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void onOrderNotification_shouldSaveNotification_withOrderIdAndCustomerNameInPayload() {
        when(orderService.getCustomerIdByOrderId(100L)).thenReturn(7L);
        User user = new User();
        user.setId(7L);
        user.setFullName("Nguyen Van A");
        when(userService.getEntityById(7L)).thenReturn(user);

        listener.onOrderNotification(new OrderNotificationEvent(NotificationType.ORDER_CONFIRMED, 100L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(saved.getReferenceId()).isEqualTo(100L);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getPayload()).contains("100").contains("Nguyen Van A");
    }

    @Test
    void onOrderNotification_shouldSkip_whenOrderNotFound() {
        when(orderService.getCustomerIdByOrderId(999L)).thenReturn(null);

        listener.onOrderNotification(new OrderNotificationEvent(NotificationType.ORDER_CANCELLED, 999L));

        verify(notificationRepository, never()).save(any());
    }
}
