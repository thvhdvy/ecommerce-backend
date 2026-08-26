package com.thanhnguyen.ecommercebackend.payment.repository;

import com.thanhnguyen.ecommercebackend.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    @Modifying
    @Query(value = "INSERT INTO payment_webhook_events (vnp_transaction_no, event_type, payload, processed_at) "
            + "VALUES (:vnpTransactionNo, :eventType, :payload, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (vnp_transaction_no) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("vnpTransactionNo") String vnpTransactionNo,
            @Param("eventType") String eventType,
            @Param("payload") String payload);

    List<PaymentWebhookEvent> findByEventTypeOrderByProcessedAtDesc(String eventType);
}
