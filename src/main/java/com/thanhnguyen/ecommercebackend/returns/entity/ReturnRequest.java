package com.thanhnguyen.ecommercebackend.returns.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@Data
@NoArgsConstructor
public class ReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference, khong FK — Order la module giao dich cot loi (design doc muc 0.8/7.4).
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Snapshot tu order_item.sellerId luc tao request — reference, khong FK.
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnReason reason;

    @Column(length = 1000)
    private String note;

    // Da prorate theo discount_ratio cua order tai thoi diem tao — xem cong thuc design doc muc 7.3.
    // KHONG phai unitPriceSnapshot*quantity nguyen gia neu order co dung coupon.
    @Column(name = "refund_amount_snapshot", nullable = false)
    private BigDecimal refundAmountSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnRequestStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "item_received_at")
    private LocalDateTime itemReceivedAt;

    // Set khi APPROVED (now + RETURN_SHIP_BACK_DAYS) — dung cho scheduled job auto-expire.
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ReturnRequest(Long orderId, Long orderItemId, User user, Long sellerId, ReturnReason reason,
                          String note, BigDecimal refundAmountSnapshot) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.user = user;
        this.sellerId = sellerId;
        this.reason = reason;
        this.note = note;
        this.refundAmountSnapshot = refundAmountSnapshot;
        this.status = ReturnRequestStatus.REQUESTED;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
