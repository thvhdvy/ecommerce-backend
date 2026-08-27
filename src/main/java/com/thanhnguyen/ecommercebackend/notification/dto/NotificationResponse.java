package com.thanhnguyen.ecommercebackend.notification.dto;

import com.thanhnguyen.ecommercebackend.notification.entity.NotificationChannel;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long userId;
    private NotificationType type;
    private NotificationChannel channel;
    private Long referenceId;
    private String payload;
    private NotificationStatus status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
