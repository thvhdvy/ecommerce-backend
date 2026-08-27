package com.thanhnguyen.ecommercebackend.notification.service;

import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import com.thanhnguyen.ecommercebackend.notification.repository.NotificationRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserService userService;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private static User customer(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Nguyen Van A");
        return u;
    }

    private Notification pendingNotification() {
        User user = customer(1L, "customer@example.com");
        Notification n = new Notification(user, NotificationType.ORDER_CONFIRMED, 100L,
                "{\"orderId\":100,\"customerName\":\"Nguyen Van A\"}");
        n.setId(1L);
        return n;
    }

    @Test
    void dispatchDue_shouldMarkSent_whenEmailSendsSuccessfully() {
        Notification notification = pendingNotification();
        when(notificationRepository.findIdsDueForDispatch(eq(NotificationStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(userService.getEntityById(1L)).thenReturn(customer(1L, "customer@example.com"));

        dispatcher.dispatchDue();

        verify(emailSender).send(eq("customer@example.com"), anyString(), anyString());
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void dispatchDue_shouldIncrementAttemptAndBackoff_whenSendFailsButBelowMaxAttempts() {
        Notification notification = pendingNotification();
        notification.setAttemptCount(1);
        when(notificationRepository.findIdsDueForDispatch(eq(NotificationStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(userService.getEntityById(1L)).thenReturn(customer(1L, "customer@example.com"));
        doThrow(new RuntimeException("SMTP down")).when(emailSender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(2);
        assertThat(notification.getNextRetryAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void dispatchDue_shouldMarkFailed_whenMaxAttemptsReached() {
        Notification notification = pendingNotification();
        notification.setAttemptCount(4); // lan nay se thanh 5 = MAX_ATTEMPTS
        when(notificationRepository.findIdsDueForDispatch(eq(NotificationStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(userService.getEntityById(1L)).thenReturn(customer(1L, "customer@example.com"));
        doThrow(new RuntimeException("SMTP down")).when(emailSender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void dispatchDue_shouldSkip_whenNotificationNoLongerPending() {
        Notification notification = pendingNotification();
        notification.setStatus(NotificationStatus.SENT);
        when(notificationRepository.findIdsDueForDispatch(eq(NotificationStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatchDue();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void dispatchDue_shouldContinueOtherIds_whenOneThrowsUnexpectedly() {
        when(notificationRepository.findIdsDueForDispatch(eq(NotificationStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        when(notificationRepository.findById(1L)).thenThrow(new RuntimeException("db blip"));
        Notification second = pendingNotification();
        second.setId(2L);
        when(notificationRepository.findById(2L)).thenReturn(Optional.of(second));
        when(userService.getEntityById(1L)).thenReturn(customer(1L, "customer@example.com"));

        dispatcher.dispatchDue();

        verify(notificationRepository, times(1)).save(second);
    }
}
