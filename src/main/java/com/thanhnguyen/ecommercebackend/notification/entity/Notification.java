package com.thanhnguyen.ecommercebackend.notification.entity;

import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    // Reference, khong FK — order_id hoac tuong tu tuy `type`, chi de trace/debug (design doc muc 8.5).
    @Column(name = "reference_id")
    private Long referenceId;

    // Template variables da snapshot luc tao (JSON) — khong query lai du lieu goc luc gui, tranh
    // truong hop order da doi tiep sau do lam sai lech noi dung email (design doc muc 8.5).
    @Column(nullable = false, length = 2000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public Notification(User user, NotificationType type, Long referenceId, String payload) {
        this.user = user;
        this.type = type;
        this.channel = NotificationChannel.EMAIL;
        this.referenceId = referenceId;
        this.payload = payload;
        this.status = NotificationStatus.PENDING;
        this.attemptCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
