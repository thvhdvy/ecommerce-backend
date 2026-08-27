package com.thanhnguyen.ecommercebackend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private Long id;
    private Long sellerId;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
