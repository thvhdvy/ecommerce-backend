package com.thanhnguyen.ecommercebackend.payout.dto;

import com.thanhnguyen.ecommercebackend.payout.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private Long id;
    private Long sellerId;
    private Long orderId;
    private Long returnRequestId;
    private LedgerEntryType type;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private LocalDateTime createdAt;
}
