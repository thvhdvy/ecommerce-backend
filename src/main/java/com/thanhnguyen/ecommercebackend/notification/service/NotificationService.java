package com.thanhnguyen.ecommercebackend.notification.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.notification.dto.NotificationResponse;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    /** Admin xem/debug — statusFilter null = toan bo. */
    PageResponse<NotificationResponse> listAll(NotificationStatus statusFilter, Pageable pageable);

    /** Admin: reset attempt_count + next_retry_at=now, cho NotificationDispatcher nhat lai o luot quet ke tiep. */
    NotificationResponse retry(Long id);
}
