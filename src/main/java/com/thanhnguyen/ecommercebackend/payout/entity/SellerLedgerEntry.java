package com.thanhnguyen.ecommercebackend.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_ledger_entries")
@Data
@NoArgsConstructor
public class SellerLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference, khong FK — cung nhom bang giao dich cot loi voi Order/Return (design doc v2 muc 9.6).
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    // Set khi type = EARNED, null khi type = ADJUSTED.
    @Column(name = "order_id")
    private Long orderId;

    // Set khi type = ADJUSTED, null khi type = EARNED.
    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    // Null khi type = ADJUSTED — chi EARNED moi co gross/commission breakdown.
    @Column(name = "gross_amount")
    private BigDecimal grossAmount;

    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    // Duong neu EARNED, am neu ADJUSTED — day la so thuc te duoc cong/tru vao seller_balances.balance.
    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static SellerLedgerEntry earned(Long sellerId, Long orderId, BigDecimal grossAmount,
                                            BigDecimal commissionAmount, BigDecimal netAmount) {
        SellerLedgerEntry entry = new SellerLedgerEntry();
        entry.sellerId = sellerId;
        entry.orderId = orderId;
        entry.type = LedgerEntryType.EARNED;
        entry.grossAmount = grossAmount;
        entry.commissionAmount = commissionAmount;
        entry.netAmount = netAmount;
        return entry;
    }

    public static SellerLedgerEntry adjusted(Long sellerId, Long returnRequestId, BigDecimal netAmount) {
        SellerLedgerEntry entry = new SellerLedgerEntry();
        entry.sellerId = sellerId;
        entry.returnRequestId = returnRequestId;
        entry.type = LedgerEntryType.ADJUSTED;
        entry.netAmount = netAmount;
        return entry;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
