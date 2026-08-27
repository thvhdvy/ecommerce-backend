package com.thanhnguyen.ecommercebackend.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import com.thanhnguyen.ecommercebackend.notification.repository.NotificationRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Goi email that (SMTP) — NGOAI transaction, khong duoc chay ben trong luc dang giu lock/transaction
 * cua nghiep vu chinh (cung nguyen tac voi refund VNPay — design doc v2 muc 8.3). Moi
 * Notification.save() la 1 thao tac don-entity, JpaRepository.save() tu mo transaction rieng cho
 * chinh no nen khong can bean/REQUIRES_NEW rieng nhu RefundLedger (khong co chuoi ghi da-buoc nao
 * can atomic o day).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void dispatchDue() {
        List<Long> ids = notificationRepository.findIdsDueForDispatch(
                NotificationStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        for (Long id : ids) {
            try {
                dispatchOne(id);
            } catch (Exception ex) {
                log.error("Failed to dispatch notification {}", id, ex);
            }
        }
    }

    private void dispatchOne(Long id) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return; // da xu ly trong luc cho — bo qua
        }

        // Doc email hien tai qua UserService (khong dung email snapshot tu luc tao) — design doc
        // muc 8.2: thong bao luon nen toi dung dia chi lien he hien tai cua khach.
        User user = userService.getEntityById(notification.getUser().getId());
        String[] subjectAndBody = buildEmail(notification);

        try {
            emailSender.send(user.getEmail(), subjectAndBody[0], subjectAndBody[1]);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Email send failed for notification {}", id, ex);
            int attempts = notification.getAttemptCount() + 1;
            notification.setAttemptCount(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                notification.setStatus(NotificationStatus.FAILED);
            } else {
                notification.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes(attempts)));
            }
        }
        notificationRepository.save(notification);
    }

    private long backoffMinutes(int attempt) {
        return 1L << attempt; // 2, 4, 8, 16 phut — exponential backoff don gian
    }

    @SuppressWarnings("unchecked")
    private String[] buildEmail(Notification notification) {
        Map<String, Object> vars;
        try {
            vars = objectMapper.readValue(notification.getPayload(), Map.class);
        } catch (Exception ex) {
            vars = Map.of();
        }
        String customerName = String.valueOf(vars.getOrDefault("customerName", "khach hang"));
        Long orderId = notification.getReferenceId();

        String subject = switch (notification.getType()) {
            case ORDER_CONFIRMED -> "Don hang #" + orderId + " da duoc xac nhan";
            case ORDER_SHIPPED -> "Don hang #" + orderId + " dang duoc giao";
            case ORDER_DELIVERED -> "Don hang #" + orderId + " da giao thanh cong";
            case ORDER_CANCELLED -> "Don hang #" + orderId + " da bi huy";
            case ORDER_REFUNDED -> "Don hang #" + orderId + " da duoc hoan tien";
        };
        String body = "Xin chao " + customerName + ",\n\n" + subject + ".\n\nCam on ban da mua sam cung chung toi.";
        return new String[]{subject, body};
    }
}
