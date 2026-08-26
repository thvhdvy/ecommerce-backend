package com.thanhnguyen.ecommercebackend.payment.repository;

import com.thanhnguyen.ecommercebackend.payment.entity.PaymentWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    // ON CONFLICT DO NOTHING khong chi dinh target: bat conflict tu ca 2 partial unique index (V19)
    // — nhom processing (PAYMENT_RESULT/QUERY_RECONCILE chung barrier theo vnp_transaction_no) va
    // AMOUNT_MISMATCH (dedupe rieng, khong chan nhom processing).
    @Modifying
    @Query(value = "INSERT INTO payment_webhook_events (vnp_transaction_no, event_type, payload, processed_at) "
            + "VALUES (:vnpTransactionNo, :eventType, :payload, CURRENT_TIMESTAMP) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("vnpTransactionNo") String vnpTransactionNo,
            @Param("eventType") String eventType,
            @Param("payload") String payload);

    Page<PaymentWebhookEvent> findByEventType(String eventType, Pageable pageable);
}
