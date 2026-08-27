package com.thanhnguyen.ecommercebackend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerBalanceResponse {
    private Long sellerId;
    private BigDecimal balance;
}
