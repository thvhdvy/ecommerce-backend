package com.thanhnguyen.ecommercebackend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {
    private Long orderId;
    private String paymentUrl;
    private BigDecimal amount;
}
