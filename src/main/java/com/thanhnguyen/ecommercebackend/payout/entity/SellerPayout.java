package com.thanhnguyen.ecommercebackend.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Khong con PENDING/PAID — pay-out la 1 hanh dong duy nhat, created_at CHINH LA thoi diem tra tien
// (design doc v2 muc 9.6, mo hinh running-balance).
@Entity
@Table(name = "seller_payouts")
@Data
@NoArgsConstructor
public class SellerPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SellerPayout(Long sellerId, BigDecimal amount) {
        this.sellerId = sellerId;
        this.amount = amount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
