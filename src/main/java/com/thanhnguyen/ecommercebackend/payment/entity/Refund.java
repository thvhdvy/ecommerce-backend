package com.thanhnguyen.ecommercebackend.payment.entity;

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
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Data
@NoArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "vnp_refund_transaction_no")
    private String vnpRefundTransactionNo;

    @Column
    private String reason;

    @Column(name = "resolution_note")
    private String resolutionNote;

    // Reference, khong FK — chi khac null khi refund nay phat sinh tu module Return (phan biet voi
    // refund do cancel order thong thuong, design doc v2 muc 7.3).
    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public Refund(Payment payment, Long orderId, BigDecimal amount, String reason) {
        this.payment = payment;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.REFUND_PENDING;
    }

    public Refund(Payment payment, Long orderId, BigDecimal amount, String reason, Long returnRequestId) {
        this(payment, orderId, amount, reason);
        this.returnRequestId = returnRequestId;
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
