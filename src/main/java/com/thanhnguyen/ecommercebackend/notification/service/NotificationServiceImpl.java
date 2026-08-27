package com.thanhnguyen.ecommercebackend.notification.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.notification.dto.NotificationResponse;
import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import com.thanhnguyen.ecommercebackend.notification.exception.NotificationNotFoundException;
import com.thanhnguyen.ecommercebackend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listAll(NotificationStatus statusFilter, Pageable pageable) {
        Page<Notification> page = statusFilter == null
                ? notificationRepository.findAll(pageable)
                : notificationRepository.findAllByStatusOrderByCreatedAtDesc(statusFilter, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional
    public NotificationResponse retry(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));

        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttemptCount(0);
        notification.setNextRetryAt(LocalDateTime.now());

        return toResponse(notificationRepository.save(notification));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getUser().getId(), n.getType(), n.getChannel(), n.getReferenceId(),
                n.getPayload(), n.getStatus(), n.getAttemptCount(), n.getNextRetryAt(),
                n.getCreatedAt(), n.getSentAt());
    }
}
