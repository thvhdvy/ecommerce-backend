package com.thanhnguyen.ecommercebackend.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// version bao ve duong ghi thu cong (khong co trong scope v1), khong bao ve duong conditional-UPDATE
// hot path (SellerBalanceRepository.addBalance/subtractBalance) — cung pattern voi Inventory/Coupon
// (design doc v2 muc 9.6).
@Entity
@Table(name = "seller_balances")
@Data
@NoArgsConstructor
public class SellerBalance {
    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SellerBalance(Long sellerId) {
        this.sellerId = sellerId;
        this.balance = BigDecimal.ZERO;
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
