package com.thanhnguyen.ecommercebackend.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_webhook_events")
@Data
@NoArgsConstructor
public class PaymentWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Khong unique=true don cot nua: idempotency scope theo 2 partial unique index trong V19
    // (nhom PAYMENT_RESULT/QUERY_RECONCILE chung barrier; AMOUNT_MISMATCH dedupe rieng) —
    // partial index khong bieu dien duoc bang JPA annotation, schema do Flyway so huu.
    @Column(name = "vnp_transaction_no", nullable = false)
    private String vnpTransactionNo;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
