package com.thanhnguyen.ecommercebackend.notification.scheduler;

import com.thanhnguyen.ecommercebackend.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {

    private final NotificationDispatcher notificationDispatcher;

    @Scheduled(fixedRate = 60_000)
    public void dispatchDueNotifications() {
        notificationDispatcher.dispatchDue();
    }
}
